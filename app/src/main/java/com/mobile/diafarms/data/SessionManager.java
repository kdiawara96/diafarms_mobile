package com.mobile.diafarms.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.mobile.diafarms.models.User;

public class SessionManager {
    private static final String PREF_NAME = "DiaFarmsSession";
    private static final String KEY_USER = "current_user";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_CURRENT_PROJET = "current_projet";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Gson gson;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
        gson = new Gson();
    }

    public void createSession(User user, String token) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER, gson.toJson(user));
        editor.putString(KEY_TOKEN, token);
        if (user.getProjetsAssignes() != null && !user.getProjetsAssignes().isEmpty()) {
            editor.putString(KEY_CURRENT_PROJET, user.getProjetsAssignes().get(0));
        }
        editor.apply();
    }

    public User getCurrentUser() {
        String userJson = pref.getString(KEY_USER, null);
        if (userJson != null) {
            return gson.fromJson(userJson, User.class);
        }
        return null;
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getToken() {
        return pref.getString(KEY_TOKEN, null);
    }

    public String getCurrentProjetId() {
        return pref.getString(KEY_CURRENT_PROJET, null);
    }

    public void setCurrentProjetId(String projetId) {
        editor.putString(KEY_CURRENT_PROJET, projetId);
        editor.apply();
    }

    public void clearSession() {
        editor.clear();
        editor.apply();
    }

    public boolean isQRValid() {
        User user = getCurrentUser();
        if (user == null || user.getQrExpiry() == 0) return false;
        return System.currentTimeMillis() < user.getQrExpiry();
    }
}