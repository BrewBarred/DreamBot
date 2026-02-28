package main.data;

import java.util.Map;

public class ActionData {
    private String type;
    private Map<String, String> params;

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
