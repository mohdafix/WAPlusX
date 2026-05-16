package com.wmods.wppenhacer.ui.models;

import org.json.JSONException;
import org.json.JSONObject;

public class PropOverride {
    private int id;
    private String description;
    private String type; // "boolean" or "integer"
    private boolean booleanValue;
    private int integerValue;

    public PropOverride(int id, String description, String type) {
        this.id = id;
        this.description = description;
        this.type = type;
    }

    public PropOverride() {}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isBooleanValue() {
        return booleanValue;
    }

    public void setBooleanValue(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public int getIntegerValue() {
        return integerValue;
    }

    public void setIntegerValue(int integerValue) {
        this.integerValue = integerValue;
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("description", description);
        obj.put("type", type);
        if ("boolean".equals(type)) {
            obj.put("value", booleanValue);
        } else {
            obj.put("value", integerValue);
        }
        return obj;
    }

    public static PropOverride fromJsonObject(JSONObject obj) throws JSONException {
        PropOverride prop = new PropOverride();
        prop.id = obj.getInt("id");
        prop.description = obj.optString("description", "");
        prop.type = obj.getString("type");
        if ("boolean".equals(prop.type)) {
            prop.booleanValue = obj.getBoolean("value");
        } else {
            prop.integerValue = obj.getInt("value");
        }
        return prop;
    }
}
