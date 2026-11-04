package com.mobile.diafarms.util;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mobile.diafarms.R;

/**
 * Textes d'aide repliés en un petit lien « 💡 Explication » : un clic ouvre une petite
 * fenêtre avec le texte complet. L'écran garde ainsi seulement les libellés, les valeurs
 * et les alertes ; le contenu de l'aide reste le même.
 *
 * En XML : android:tag="explication" (ou "explication:Titre de la fenêtre") sur le
 * TextView d'aide, puis {@link #appliquer(View)} sur la racine de l'écran.
 */
public final class Explication {

    public static final String LIBELLE = "💡 Explication";
    private static final String TAG = "explication";
    private static final String TAG_FAIT = "explication_faite";

    private Explication() {}

    /** Transforme chaque TextView marqué android:tag="explication[:Titre]" de l'arbre. */
    public static void appliquer(View racine) {
        if (racine == null) return;
        Object tag = racine.getTag();
        if (racine instanceof TextView && tag instanceof String && ((String) tag).startsWith(TAG)
                && !TAG_FAIT.equals(tag)) {
            String t = (String) tag;
            String titre = t.length() > TAG.length() + 1 && t.charAt(TAG.length()) == ':'
                    ? t.substring(TAG.length() + 1).trim() : null;
            transformer((TextView) racine, titre);
        }
        if (racine instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) racine;
            for (int i = 0; i < g.getChildCount(); i++) appliquer(g.getChildAt(i));
        }
    }

    /** Remplace le texte d'aide du TextView par le lien, le texte complet va dans la fenêtre. */
    public static void transformer(TextView aide, CharSequence titre) {
        if (aide == null) return;
        CharSequence texte = aide.getText();
        if (texte == null || texte.toString().trim().isEmpty() || LIBELLE.contentEquals(texte)) return;
        lier(aide, titre, texte);
    }

    public static void transformer(TextView aide) {
        transformer(aide, null);
    }

    /** Fait du TextView un lien « 💡 Explication » qui ouvre le texte donné. */
    public static void lier(TextView lien, CharSequence texte) {
        lier(lien, null, texte);
    }

    public static void lier(TextView lien, CharSequence titre, CharSequence texte) {
        if (lien == null) return;
        Context ctx = lien.getContext();
        lien.setText(LIBELLE);
        lien.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
        lien.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int p = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, ctx.getResources().getDisplayMetrics()));
        lien.setPadding(lien.getPaddingLeft(), p, lien.getPaddingRight(), p);
        lien.setClickable(true);
        lien.setFocusable(true);
        lien.setTag(TAG_FAIT);
        lien.setOnClickListener(v -> afficher(ctx, titre, texte));
    }

    /** Petite fenêtre : titre facultatif, texte complet, bouton « Fermer ». */
    public static void afficher(Context ctx, CharSequence titre, CharSequence texte) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx)
                .setMessage(texte)
                .setPositiveButton("Fermer", null);
        if (titre != null && titre.length() > 0) b.setTitle(titre);
        b.show();
    }
}
