package com.mobile.diafarms.util;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

/**
 * Étoile rouge des champs obligatoires, même convention que le web (composant Label) :
 * tout libellé qui finit par « * » (« Client * ») affiche son étoile en rouge.
 * Le texte reste le même (toString() inchangé), seule la couleur de l'étoile change.
 */
public final class ChampObligatoire {

    private static final int ROUGE = Color.parseColor("#DC2626");

    private ChampObligatoire() {}

    /** « Client * » avec l'étoile en rouge ; tout autre texte est rendu tel quel. */
    public static CharSequence etoile(CharSequence texte) {
        if (texte == null) return null;
        String s = texte.toString();
        String fin = s.replaceAll("\\s+$", "");
        if (!fin.endsWith("*")) return texte;
        SpannableString sp = new SpannableString(fin);
        sp.setSpan(new ForegroundColorSpan(ROUGE), fin.length() - 1, fin.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    /** Pose l'étoile rouge sur toute une vue : indices des TextInputLayout et libellés TextView. */
    public static void appliquer(View v) {
        if (v == null) return;
        if (v instanceof TextInputLayout) {
            TextInputLayout til = (TextInputLayout) v;
            CharSequence h = til.getHint();
            if (h != null && !(h instanceof Spanned)) til.setHint(etoile(h));
        } else if (v instanceof TextView && !(v instanceof EditText)) {
            TextView tv = (TextView) v;
            CharSequence t = tv.getText();
            if (t != null && !(t instanceof Spanned) && t.toString().trim().endsWith("*")) tv.setText(etoile(t));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) appliquer(g.getChildAt(i));
        }
    }
}
