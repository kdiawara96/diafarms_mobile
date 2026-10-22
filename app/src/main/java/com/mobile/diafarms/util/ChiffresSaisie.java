package com.mobile.diafarms.util;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.mobile.diafarms.R;

/** Chiffres groupés par milliers pendant la frappe (« 10 000 000 », « 62,5 ») dans les
 * champs numériques. Les espaces ne sont qu'affichés : {@link #brut} les retire avant
 * toute lecture du nombre. */
public final class ChiffresSaisie {

    private ChiffresSaisie() {}

    /** Retire les espaces de groupement (normales et insécables). */
    public static String brut(CharSequence s) {
        return s == null ? "" : s.toString().replaceAll("[\\s\\u00A0\\u202F]", "");
    }

    /** Applique le formatage à tous les champs numériques sous {@code racine}. À appeler
     * après tout setInputType sur ces champs (il remplace leur clavier). */
    public static void appliquer(View racine) {
        if (racine instanceof EditText) {
            EditText e = (EditText) racine;
            if ((e.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER) formater(e);
        } else if (racine instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) racine;
            for (int i = 0; i < g.getChildCount(); i++) appliquer(g.getChildAt(i));
        }
    }

    public static void formater(EditText e) {
        if (e.getTag(R.id.tag_chiffres_saisie) instanceof Watcher) return; // déjà fait
        boolean decimales = (e.getInputType() & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0;
        int type = e.getInputType();
        // Le clavier chiffres refuse l'espace : on l'autorise, puis on remet le clavier numérique.
        e.setKeyListener(DigitsKeyListener.getInstance(decimales ? "0123456789 ,." : "0123456789 "));
        e.setRawInputType(type);
        Watcher w = new Watcher(e);
        e.setTag(R.id.tag_chiffres_saisie, w);
        e.addTextChangedListener(w);
        w.afterTextChanged(e.getText());
    }

    /** « 1234567,5 » devient « 1 234 567,5 » (le séparateur décimal tapé est gardé). */
    static String grouper(String chiffres) {
        int sep = -1;
        for (int i = 0; i < chiffres.length(); i++) {
            char c = chiffres.charAt(i);
            if (c == ',' || c == '.') { sep = i; break; }
        }
        String entier = sep < 0 ? chiffres : chiffres.substring(0, sep);
        String reste = sep < 0 ? "" : chiffres.substring(sep);
        StringBuilder sb = new StringBuilder();
        int n = entier.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) sb.append(' ');
            sb.append(entier.charAt(i));
        }
        return sb.append(reste).toString();
    }

    private static final class Watcher implements TextWatcher {
        private final EditText champ;
        private boolean enCours;

        Watcher(EditText champ) { this.champ = champ; }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (enCours || s == null) return;
            String avant = s.toString();
            String apres = grouper(brut(avant));
            if (apres.equals(avant)) return;
            // Curseur gardé après le même nombre de caractères utiles (hors espaces).
            int curseur = Math.max(0, Math.min(champ.getSelectionEnd(), avant.length()));
            int utiles = brut(avant.substring(0, curseur)).length();
            int pos = 0;
            while (pos < apres.length() && utiles > 0) {
                if (apres.charAt(pos) != ' ') utiles--;
                pos++;
            }
            enCours = true;
            s.replace(0, s.length(), apres);
            enCours = false;
            champ.setSelection(Math.min(pos, champ.length()));
        }
    }
}
