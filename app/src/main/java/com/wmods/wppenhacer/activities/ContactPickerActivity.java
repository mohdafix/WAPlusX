package com.wmods.wppenhacer.activities;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.adapter.ContactPickerAdapter;
import com.wmods.wppenhacer.databinding.ActivityContactPickerBinding;
import com.wmods.wppenhacer.preference.ContactData;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import java.util.ArrayList;
import java.util.List;

public class ContactPickerActivity extends BaseActivity {

    private ActivityContactPickerBinding binding;
    private ContactPickerAdapter adapter;
    private List<ContactData> allContacts = new ArrayList<>();
    private List<ContactData> selectedContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityContactPickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_CONTACTS}, 100);
        } else {
            loadContacts();
        }

        setupRecyclerView();
        setupListeners();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        } else {
            Toast.makeText(this, "Permission required to read contacts", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadContacts() {
        new Thread(() -> {
            List<ContactData> contacts = fetchContacts();
            if (contacts.isEmpty()) {
                contacts = fetchAndroidContacts();
            }
            final List<ContactData> finalContacts = contacts;
            runOnUiThread(() -> {
                allContacts.clear();
                allContacts.addAll(finalContacts);
                if (allContacts.isEmpty()) {
                    Toast.makeText(this, "No contacts found", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    if (adapter != null) {
                        adapter.filter(""); // This populates the adapter's filteredContacts
                        adapter.notifyDataSetChanged();
                    }
                }
            });
        }).start();
    }

    private List<ContactData> fetchContacts() {
        List<ContactData> contacts = new ArrayList<>();
        java.util.Set<String> seenJids = new java.util.HashSet<>();
        
        try {
            Cursor cursor = getContentResolver().query(
                ContactsContract.Data.CONTENT_URI,
                new String[]{
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.Data.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Data.CONTACT_ID
                },
                ContactsContract.Data.MIMETYPE + "=?",
                new String[]{"vnd.android.cursor.item/vnd.com.whatsapp.profile"},
                ContactsContract.Data.DISPLAY_NAME + " ASC"
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String phone = cursor.getString(1);
                    String photoUri = cursor.getString(2);
                    
                    if (name != null && phone != null && !phone.isEmpty()) {
                        String rawPhone = phone.replaceAll("[^0-9]", "");
                        String jid = rawPhone + "@s.whatsapp.net";
                        
                        if (!seenJids.contains(jid)) {
                            contacts.add(new ContactData(name, jid, photoUri));
                            seenJids.add(jid);
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return contacts;
    }

    private List<ContactData> fetchAndroidContacts() {
        List<ContactData> fallbackContacts = new ArrayList<>();
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
            Cursor cursor = getContentResolver().query(uri, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    String cleanNumber = number.replaceAll("[^0-9+]", "");
                    if (cleanNumber.startsWith("+")) {
                        // Keep as is
                    } else if (cleanNumber.length() == 10) {
                        cleanNumber = "+" + cleanNumber;
                    }
                    String jid = cleanNumber + "@s.whatsapp.net";
                    fallbackContacts.add(new ContactData(name, jid));
                }
                cursor.close();
                android.util.Log.d("WaEnhancer", "Android WA fallback loaded: " + fallbackContacts.size());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fallbackContacts;
    }

    private void setupRecyclerView() {
        adapter = new ContactPickerAdapter(allContacts, selectedContacts, (contact, selected) -> {
            if (selected) {
                selectedContacts.add(contact);
            } else {
                selectedContacts.remove(contact);
            }
        });
        binding.contactListView.setLayoutManager(new LinearLayoutManager(this));
        binding.contactListView.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    adapter.filter(s != null ? s.toString() : "");
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        binding.selectAllButton.setOnClickListener(v -> {
            selectedContacts.clear();
            selectedContacts.addAll(allContacts);
            adapter.notifyDataSetChanged();
        });

        binding.saveButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selected_contacts", new ArrayList<>(selectedContacts));
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}
