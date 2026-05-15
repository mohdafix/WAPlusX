package com.wmods.wppenhacer.xposed.features.others;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyInstantsClient {
    private static final String TAG = "MyInstantsClient";
    private static final String BASE_URL = "https://www.myinstants.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static class SoundItem {
        public String title;
        public String pageUrl;
        public String mp3Url;
        public String slug;

        @Override
        public String toString() {
            return "SoundItem{" + "title='" + title + '\'' + ", mp3Url='" + mp3Url + '\'' + '}';
        }
    }

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(Exception e);
    }

    public void search(String query, Callback<List<SoundItem>> callback) {
        new Thread(() -> {
            try {
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                String url = BASE_URL + "/en/search/?name=" + encodedQuery;
                Log.d(TAG, "Searching: " + url);
                String html = fetchHtml(url, true);
                Log.d(TAG, "Fetched HTML length: " + html.length());
                List<SoundItem> results = parseHtml(html);
                Log.d(TAG, "Parsed " + results.size() + " results");
                callback.onSuccess(results);
            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage(), e);
                callback.onError(e);
            }
        }).start();
    }

    public void getTrending(Callback<List<SoundItem>> callback) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/en/index/us/";
                Log.d(TAG, "Fetching trending: " + url);
                String html = fetchHtml(url, false);
                Log.d(TAG, "Fetched HTML length: " + html.length());
                List<SoundItem> results = parseHtml(html);
                Log.d(TAG, "Parsed " + results.size() + " trending results");
                callback.onSuccess(results);
            } catch (Exception e) {
                Log.e(TAG, "Trending error: " + e.getMessage(), e);
                callback.onError(e);
            }
        }).start();
    }

    private String fetchHtml(String urlString, boolean allow404AsEmpty) throws Exception {
        URL url = java.net.URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "HTTP response code: " + responseCode);
        if (allow404AsEmpty && responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return "";
        }
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("HTTP response code: " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine).append("\n");
        }
        in.close();
        return response.toString();
    }

    private List<SoundItem> parseHtml(String html) {
        List<SoundItem> items = new ArrayList<>();

        // Current MyInstants HTML structure (2024+):
        // <div class="instant">
        // <button class="small-button" onclick="play('/media/sounds/movie_1.mp3',
        // 'loader-23010', 'bruh')" ...></button>
        // <a href="/en/instant/bruh/" class="instant-link link-secondary">BRUH</a>
        // </div>

        // Primary pattern: extract mp3 from onclick="play('...')" and title from
        // instant-link
        Pattern pattern = Pattern.compile(
                "<div class=\"instant\">[^<]*(?:<[^>]+>[^<]*)*?onclick=\"play\\('([^']+)'[^\"]*\\)\"[^<]*(?:<[^>]+>[^<]*)*?href=\"([^\"]+)\"[^>]*class=\"instant-link[^\"]*\">([^<]+)</a>",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            SoundItem item = new SoundItem();
            item.mp3Url = toAbsoluteUrl(matcher.group(1));
            item.pageUrl = toAbsoluteUrl(matcher.group(2));
            item.title = matcher.group(3).trim();

            // Extract slug from pageUrl
            String[] parts = matcher.group(2).split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isEmpty()) {
                    item.slug = parts[i];
                    break;
                }
            }

            items.add(item);
            Log.d(TAG, "Found: " + item.title + " -> " + item.mp3Url);
        }

        if (items.isEmpty()) {
            Log.d(TAG, "Primary pattern found 0 results, trying simpler patterns...");

            // Simpler fallback: just find all play() calls and instant-link pairs
            // Step 1: Find all play() calls to get mp3 URLs
            List<String> mp3Urls = new ArrayList<>();
            Pattern playPattern = Pattern.compile("play\\('([^']+)'");
            Matcher playMatcher = playPattern.matcher(html);
            while (playMatcher.find()) {
                mp3Urls.add(toAbsoluteUrl(playMatcher.group(1)));
            }

            // Step 2: Find all instant-link entries
            List<String[]> links = new ArrayList<>();
            Pattern linkPattern = Pattern
                    .compile("href=\"(/en/instant/[^\"]+)\"[^>]*class=\"instant-link[^\"]*\">([^<]+)</a>");
            Matcher linkMatcher = linkPattern.matcher(html);
            while (linkMatcher.find()) {
                links.add(new String[] { linkMatcher.group(1), linkMatcher.group(2).trim() });
            }

            Log.d(TAG, "Fallback found " + mp3Urls.size() + " play() calls, " + links.size() + " instant-links");

            // Pair them up (they appear in same order on the page)
            int count = Math.min(mp3Urls.size(), links.size());
            for (int i = 0; i < count; i++) {
                SoundItem item = new SoundItem();
                item.mp3Url = mp3Urls.get(i);
                item.pageUrl = toAbsoluteUrl(links.get(i)[0]);
                item.title = links.get(i)[1];

                String[] parts = links.get(i)[0].split("/");
                for (int j = parts.length - 1; j >= 0; j--) {
                    if (!parts[j].isEmpty()) {
                        item.slug = parts[j];
                        break;
                    }
                }

                items.add(item);
            }
        }

        Log.d(TAG, "Total parsed: " + items.size() + " sound items");
        return items;
    }

    private String toAbsoluteUrl(String value) {
        if (value == null || value.isEmpty()) return value;
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return BASE_URL + value;
        return BASE_URL + "/" + value;
    }
}
