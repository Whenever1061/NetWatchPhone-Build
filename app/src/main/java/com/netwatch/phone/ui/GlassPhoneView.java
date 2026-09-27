package com.netwatch.phone.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * NetWatch Phone's custom glass dialer surface. It deliberately uses only
 * platform Android drawing APIs so the app has no UI analytics or third-party
 * runtime dependencies.
 */
public final class GlassPhoneView extends View {
    public interface Callback {
        void placeCall(String number);
        void openSettings();
    }

    public static final class RecentCall {
        public final String name;
        public final String number;
        public final String detail;
        public final boolean missed;
        public RecentCall(String name, String number, String detail, boolean missed) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
            this.detail = detail == null ? "" : detail;
            this.missed = missed;
        }
    }

    public static final class ContactItem {
        public final String name;
        public final String number;
        public ContactItem(String name, String number) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
        }
    }

    private enum Page { FAVORITES, RECENTS, CONTACTS, KEYPAD }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Callback callback;
    private final List<RecentCall> recents = new ArrayList<>();
    private final List<ContactItem> contacts = new ArrayList<>();
    private final StringBuilder number = new StringBuilder();
    private Page page = Page.RECENTS;
    private float density;
    private float width;
    private float height;
    private float navTop;
    private final String[][] keypad = {
            {"1", ""}, {"2", "ABC"}, {"3", "DEF"},
            {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"},
            {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"},
            {"*", ""}, {"0", "+"}, {"#", ""}
    };

    public GlassPhoneView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
        setContentDescription("NetWatch Phone");
    }

    public void setRecentCalls(List<RecentCall> items) {
        recents.clear();
        if (items != null) recents.addAll(items);
        invalidate();
    }

    public void setContacts(List<ContactItem> items) {
        contacts.clear();
        if (items != null) contacts.addAll(items);
        invalidate();
    }

    public void showKeypad(String preset) {
        page = Page.KEYPAD;
        number.setLength(0);
        if (preset != null) number.append(preset);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        width = w;
        height = h;
        navTop = height - dp(94);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawWallpaper(canvas);
        drawHeader(canvas);
        switch (page) {
            case FAVORITES: drawFavorites(canvas); break;
            case RECENTS: drawRecents(canvas); break;
            case CONTACTS: drawContacts(canvas); break;
            case KEYPAD: drawKeypad(canvas); break;
        }
        drawBottomNav(canvas);
    }

    private void drawWallpaper(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, width, height,
                new int[]{Color.rgb(36, 73, 108), Color.rgb(17, 43, 70), Color.rgb(6, 17, 31)},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setColor(0x24FFFFFF);
        c.drawCircle(width * .18f, height * .22f, dp(125), paint);
        paint.setColor(0x1B9CD7FF);
        c.drawCircle(width * .87f, height * .28f, dp(145), paint);
        paint.setColor(0x18FFC88A);
        c.drawCircle(width * .60f, height * .56f, dp(165), paint);
        Path mountains = new Path();
        mountains.moveTo(0, height * .49f);
        mountains.lineTo(width * .16f, height * .33f);
        mountains.lineTo(width * .28f, height * .44f);
        mountains.lineTo(width * .44f, height * .25f);
        mountains.lineTo(width * .60f, height * .43f);
        mountains.lineTo(width * .76f, height * .31f);
        mountains.lineTo(width, height * .47f);
        mountains.lineTo(width, height * .68f);
        mountains.lineTo(0, height * .68f);
        mountains.close();
        paint.setColor(0x35213E59);
        c.drawPath(mountains, paint);
        paint.setShader(new LinearGradient(0, height * .52f, 0, height,
                0x253C769E, 0x3A07111E, Shader.TileMode.CLAMP));
        c.drawRect(0, height * .52f, width, height, paint);
        paint.setShader(null);
    }

    private void drawHeader(Canvas c) {
        float top = dp(36);
        drawShield(c, dp(29), top + dp(18), dp(18));
        paint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        paint.setTextSize(sp(27));
        paint.setColor(Color.WHITE);
        c.drawText("NetWatch Phone", dp(58), top + dp(18), paint);
        paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        paint.setTextSize(sp(12.5f));
        paint.setColor(0xFFBDD2E8);
        String sub = page == Page.KEYPAD ? "Private. Protected. In your control." : "Private calls for a safer, quieter you.";
        c.drawText(sub, dp(58), top + dp(39), paint);
        RectF more = new RectF(width - dp(56), top - dp(4), width - dp(16), top + dp(36));
        drawGlass(c, more, dp(20), 0x2AFFFFFF, 0x64FFFFFF);
        paint.setColor(Color.WHITE);
        c.drawCircle(more.centerX() - dp(7), more.centerY(), dp(1.6f), paint);
        c.drawCircle(more.centerX(), more.centerY(), dp(1.6f), paint);
        c.drawCircle(more.centerX() + dp(7), more.centerY(), dp(1.6f), paint);
    }

    private void drawRecents(Canvas c) {
        float y = dp(104);
        RectF search = new RectF(dp(18), y, width - dp(18), y + dp(52));
        drawGlass(c, search, dp(22), 0x25FFFFFF, 0x66FFFFFF);
        drawSearchIcon(c, dp(39), y + dp(26));
        text(c, "Search contacts, numbers, or places", dp(59), y + dp(31), sp(13), 0xFFE2EAF3, false);
        drawMic(c, width - dp(40), y + dp(25));
        y += dp(64);
        float gap = dp(7);
        float tabW = (width - dp(36) - gap * 2) / 3f;
        drawChip(c, "All", dp(18), y, tabW, true);
        drawChip(c, "Missed", dp(18) + tabW + gap, y, tabW, false);
        drawChip(c, "Voicemail", dp(18) + (tabW + gap) * 2, y, tabW, false);
        y += dp(58);
        text(c, "Today", dp(20), y, sp(15), Color.WHITE, true);
        y += dp(16);
        int max = Math.min(recents.size(), 7);
        if (max == 0) {
            drawEmptyState(c, "No recent calls yet", "Your call history will appear here.", y + dp(72));
            return;
        }
        float rowH = dp(69);
        for (int i = 0; i < max; i++) {
            RecentCall item = recents.get(i);
            RectF row = new RectF(dp(16), y, width - dp(16), y + rowH - dp(5));
            drawGlass(c, row, dp(22), 0x18FFFFFF, 0x35FFFFFF);
            float avatarX = dp(48);
            float cy = row.centerY();
            drawAvatar(c, avatarX, cy, dp(22), displayName(item), item.missed);
            text(c, displayName(item), dp(82), cy - dp(4), sp(15), Color.WHITE, false);
            text(c, item.detail, dp(82), cy + dp(18), sp(11.5f), item.missed ? 0xFFFF7080 : 0xFFC5D5E5, false);
            drawPhoneCircle(c, width - dp(43), cy, dp(18), 0x22FFFFFF, Color.WHITE);
            y += rowH;
            if (y > navTop - dp(60)) break;
        }
    }

    private void drawFavorites(Canvas c) {
        float y = dp(118);
        text(c, "Favorites", dp(20), y, sp(29), Color.WHITE, true);
        text(c, "People you call most", dp(20), y + dp(25), sp(12.5f), 0xFFBDD2E8, false);
        y += dp(50);
        int max = Math.min(contacts.size(), 6);
        if (max == 0) {
            drawEmptyState(c, "No favorites yet", "Your contacts will appear here.", y + dp(90));
            return;
        }
        for (int i = 0; i < max; i++) {
            ContactItem item = contacts.get(i);
            RectF row = new RectF(dp(18), y, width - dp(18), y + dp(72));
            drawGlass(c, row, dp(24), 0x1CFFFFFF, 0x3AFFFFFF);
            drawAvatar(c, dp(50), row.centerY(), dp(23), item.name, false);
            text(c, item.name, dp(86), row.centerY() - dp(3), sp(16), Color.WHITE, false);
            text(c, item.number, dp(86), row.centerY() + dp(18), sp(11.5f), 0xFFC4D5E6, false);
            drawPhoneCircle(c, width - dp(46), row.centerY(), dp(19), 0x24FFFFFF, Color.WHITE);
            y += dp(80);
            if (y > navTop - dp(70)) break;
        }
    }

    private void drawContacts(Canvas c) {
        float y = dp(108);
        RectF search = new RectF(dp(18), y, width - dp(18), y + dp(52));
        drawGlass(c, search, dp(22), 0x25FFFFFF, 0x66FFFFFF);
        drawSearchIcon(c, dp(39), y + dp(26));
        text(c, "Search contacts", dp(59), y + dp(31), sp(13), 0xFFE2EAF3, false);
        y += dp(72);
        text(c, "Contacts", dp(20), y, sp(26), Color.WHITE, true);
        y += dp(20);
        int max = Math.min(contacts.size(), 8);
        if (max == 0) {
            drawEmptyState(c, "No contacts available", "Grant Contacts permission from the menu.", y + dp(90));
            return;
        }
        for (int i = 0; i < max; i++) {
            ContactItem item = contacts.get(i);
            float cy = y + dp(31);
            drawAvatar(c, dp(48), cy, dp(21), item.name, false);
            text(c, item.name, dp(82), cy - dp(2), sp(15), Color.WHITE, false);
            text(c, item.number, dp(82), cy + dp(17), sp(11.5f), 0xFFC5D5E5, false);
            drawPhoneCircle(c, width - dp(43), cy, dp(18), 0x20FFFFFF, Color.WHITE);
            stroke.setColor(0x28FFFFFF);
            stroke.setStrokeWidth(dp(1));
            c.drawLine(dp(82), y + dp(62), width - dp(18), y + dp(62), stroke);
            y += dp(68);
            if (y > navTop - dp(70)) break;
        }
    }

    private void drawKeypad(Canvas c) {
        float y = dp(118);
        RectF numberBox = new RectF(dp(26), y, width - dp(26), y + dp(72));
        drawGlass(c, numberBox, dp(28), 0x2CFFFFFF, 0x73FFFFFF);
        String shown = number.length() == 0 ? "Enter a number" : number.toString();
        paint.setTextAlign(Paint.Align.CENTER);
        text(c, shown, numberBox.centerX(), numberBox.centerY() + dp(8), number.length() == 0 ? sp(17) : sp(27), number.length() == 0 ? 0xFFB6C8DB : Color.WHITE, false);
        paint.setTextAlign(Paint.Align.LEFT);
        if (number.length() > 0) {
            RectF back = new RectF(width - dp(70), y + dp(19), width - dp(38), y + dp(51));
            drawGlass(c, back, dp(14), 0x22FFFFFF, 0x45FFFFFF);
            text(c, "×", back.centerX() - dp(7), back.centerY() + dp(6), sp(21), 0xFFDDE7F1, true);
        }
        float centerX = width / 2f;
        float colGap = dp(112);
        float startX = centerX - colGap;
        float startY = y + dp(117);
        float rowGap = dp(96);
        float r = dp(37);
        for (int i = 0; i < keypad.length; i++) {
            int col = i % 3;
            int row = i / 3;
            float x = startX + col * colGap;
            float cy = startY + row * rowGap;
            drawKey(c, x, cy, r, keypad[i][0], keypad[i][1]);
        }
        float callY = startY + rowGap * 4f - dp(4);
        paint.setShadowLayer(dp(14), 0, dp(5), 0x8828FF6E);
        paint.setShader(new LinearGradient(centerX - dp(36), callY - dp(36), centerX + dp(36), callY + dp(36),
                0xFF65E961, 0xFF14A83E, Shader.TileMode.CLAMP));
        c.drawCircle(centerX, callY, dp(37), paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(0xB8FFFFFF);
        c.drawCircle(centerX, callY, dp(37), stroke);
        stroke.setStyle(Paint.Style.FILL);
        drawPhoneGlyph(c, centerX, callY, Color.WHITE, 1.25f);
    }

    private void drawKey(Canvas c, float x, float y, float r, String primary, String secondary) {
        RectF b = new RectF(x - r, y - r, x + r, y + r);
        drawGlass(c, b, r, 0x2CFFFFFF, 0x6CFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        text(c, primary, x, y + (secondary.isEmpty() ? dp(10) : dp(3)), sp(30), Color.WHITE, false);
        if (!secondary.isEmpty()) text(c, secondary, x, y + dp(23), sp(11), 0xFFE9EFF6, false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBottomNav(Canvas c) {
        RectF nav = new RectF(dp(12), navTop, width - dp(12), height - dp(12));
        drawGlass(c, nav, dp(28), 0x30071322, 0x58FFFFFF);
        String[] labels = {"Favorites", "Recents", "Contacts", "Keypad"};
        Page[] pages = {Page.FAVORITES, Page.RECENTS, Page.CONTACTS, Page.KEYPAD};
        float slot = nav.width() / 4f;
        for (int i = 0; i < 4; i++) {
            float cx = nav.left + slot * (i + .5f);
            boolean selected = page == pages[i];
            if (selected) {
                RectF selectedRect = new RectF(cx - slot * .40f, nav.top + dp(8), cx + slot * .40f, nav.bottom - dp(8));
                paint.setShadowLayer(dp(12), 0, 0, 0x884E9FFF);
                drawGlass(c, selectedRect, dp(24), 0x574A94E9, 0xB0BBDDFF);
                paint.clearShadowLayer();
            }
            float iconY = nav.top + dp(29);
            drawNavIcon(c, i, cx, iconY, selected ? Color.WHITE : 0xFFD2DAE5);
            paint.setTextAlign(Paint.Align.CENTER);
            text(c, labels[i], cx, nav.top + dp(67), sp(10.5f), selected ? Color.WHITE : 0xFFD2DAE5, false);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawNavIcon(Canvas c, int index, float cx, float cy, int color) {
        paint.setColor(color);
        stroke.setColor(color);
        stroke.setStrokeWidth(dp(2.2f));
        stroke.setStyle(Paint.Style.STROKE);
        if (index == 0) {
            Path p = new Path();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float r = i % 2 == 0 ? dp(11) : dp(4.8f);
                float x = cx + (float)Math.cos(a) * r;
                float y = cy + (float)Math.sin(a) * r;
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.close();
            c.drawPath(p, paint);
        } else if (index == 1) {
            c.drawCircle(cx, cy, dp(10), stroke);
            c.drawLine(cx, cy, cx, cy - dp(6), stroke);
            c.drawLine(cx, cy, cx + dp(5), cy + dp(2), stroke);
        } else if (index == 2) {
            c.drawCircle(cx, cy - dp(5), dp(5), paint);
            RectF b = new RectF(cx - dp(9), cy + dp(2), cx + dp(9), cy + dp(11));
            c.drawRoundRect(b, dp(6), dp(6), paint);
        } else {
            float s = dp(4);
            for (int r = -1; r <= 1; r++) for (int col = -1; col <= 1; col++)
                c.drawCircle(cx + col * dp(8), cy + r * dp(8), s, paint);
        }
        stroke.setStyle(Paint.Style.FILL);
    }

    private void drawChip(Canvas c, String label, float x, float y, float w, boolean selected) {
        RectF r = new RectF(x, y, x + w, y + dp(40));
        if (selected) {
            paint.setShadowLayer(dp(11), 0, 0, 0x774E9FFF);
            drawGlass(c, r, dp(18), 0x6B5CA6FF, 0xC2D9ECFF);
            paint.clearShadowLayer();
        } else {
            drawGlass(c, r, dp(18), 0x1CFFFFFF, 0x35FFFFFF);
        }
        paint.setTextAlign(Paint.Align.CENTER);
        text(c, label, r.centerX(), r.centerY() + dp(5), sp(12.5f), Color.WHITE, false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawGlass(Canvas c, RectF rect, float radius, int fillColor, int borderColor) {
        paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{lightenAlpha(fillColor, 1.18f), fillColor, darkenAlpha(fillColor, .78f)},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(borderColor);
        c.drawRoundRect(rect, radius, radius, stroke);
        stroke.setStyle(Paint.Style.FILL);
    }

    private static int lightenAlpha(int color, float factor) {
        int a = Math.min(255, (int)(Color.alpha(color) * factor));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int darkenAlpha(int color, float factor) {
        int a = Math.max(0, Math.min(255, (int)(Color.alpha(color) * factor)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void drawAvatar(Canvas c, float x, float y, float r, String label, boolean alert) {
        int seed = label == null ? 0 : label.hashCode();
        int base = alert ? 0xFFC64F66 : Color.rgb(69 + Math.abs(seed % 55), 96 + Math.abs((seed / 7) % 70), 125 + Math.abs((seed / 13) % 80));
        paint.setColor(base);
        c.drawCircle(x, y, r, paint);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x77FFFFFF);
        c.drawCircle(x, y, r, stroke);
        stroke.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        text(c, initials(label), x, y + dp(6), sp(14), Color.WHITE, true);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private String initials(String s) {
        if (s == null || s.trim().isEmpty()) return "?";
        String[] parts = s.trim().split("\\s+");
        String a = parts[0].substring(0, 1).toUpperCase();
        if (parts.length > 1) a += parts[parts.length - 1].substring(0, 1).toUpperCase();
        return a;
    }

    private String displayName(RecentCall item) {
        if (item.name != null && !item.name.trim().isEmpty()) return item.name;
        if (item.number != null && !item.number.trim().isEmpty()) return item.number;
        return "Unknown Caller";
    }

    private void drawPhoneCircle(Canvas c, float x, float y, float r, int bg, int fg) {
        paint.setColor(bg);
        c.drawCircle(x, y, r, paint);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x38FFFFFF);
        c.drawCircle(x, y, r, stroke);
        stroke.setStyle(Paint.Style.FILL);
        drawPhoneGlyph(c, x, y, fg, .70f);
    }

    private void drawPhoneGlyph(Canvas c, float x, float y, int color, float scale) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(5) * scale);
        RectF arc = new RectF(x - dp(12) * scale, y - dp(12) * scale, x + dp(12) * scale, y + dp(12) * scale);
        c.drawArc(arc, 133, 93, false, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawShield(Canvas c, float x, float y, float r) {
        Path p = new Path();
        p.moveTo(x, y - r);
        p.lineTo(x + r * .78f, y - r * .63f);
        p.lineTo(x + r * .67f, y + r * .43f);
        p.quadTo(x, y + r * 1.12f, x - r * .67f, y + r * .43f);
        p.lineTo(x - r * .78f, y - r * .63f);
        p.close();
        paint.setShader(new LinearGradient(x - r, y - r, x + r, y + r, 0xFF7ED9FF, 0xFF1D79D5, Shader.TileMode.CLAMP));
        paint.setShadowLayer(dp(10), 0, 0, 0x995FCBFF);
        c.drawPath(p, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.4f));
        stroke.setColor(0xCCDAF5FF);
        c.drawPath(p, stroke);
        stroke.setStyle(Paint.Style.FILL);
        paint.setColor(0xD9FFFFFF);
        c.drawCircle(x, y, dp(3.3f), paint);
    }

    private void drawSearchIcon(Canvas c, float x, float y) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(0xFFE4EDF6);
        c.drawCircle(x, y - dp(2), dp(7), stroke);
        c.drawLine(x + dp(5), y + dp(3), x + dp(11), y + dp(9), stroke);
        stroke.setStyle(Paint.Style.FILL);
    }

    private void drawMic(Canvas c, float x, float y) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(0xFFE4EDF6);
        RectF mic = new RectF(x - dp(4), y - dp(9), x + dp(4), y + dp(3));
        c.drawRoundRect(mic, dp(4), dp(4), stroke);
        c.drawArc(new RectF(x - dp(8), y - dp(2), x + dp(8), y + dp(10)), 0, 180, false, stroke);
        c.drawLine(x, y + dp(9), x, y + dp(14), stroke);
        stroke.setStyle(Paint.Style.FILL);
    }

    private void drawEmptyState(Canvas c, String title, String sub, float y) {
        RectF card = new RectF(dp(24), y - dp(50), width - dp(24), y + dp(60));
        drawGlass(c, card, dp(28), 0x18FFFFFF, 0x35FFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        text(c, title, width / 2f, y, sp(18), Color.WHITE, true);
        text(c, sub, width / 2f, y + dp(27), sp(12), 0xFFBDD2E8, false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void text(Canvas c, String value, float x, float y, float sizePx, int color, boolean bold) {
        paint.setShader(null);
        paint.setColor(color);
        paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setTextSize(sizePx);
        paint.setStyle(Paint.Style.FILL);
        c.drawText(value == null ? "" : value, x, y, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = event.getX();
        float y = event.getY();
        if (y >= navTop) {
            float slot = (width - dp(24)) / 4f;
            int index = (int)((x - dp(12)) / slot);
            if (index >= 0 && index < 4) {
                page = Page.values()[index];
                invalidate();
                return true;
            }
        }
        if (x > width - dp(72) && y >= dp(27) && y <= dp(90)) {
            if (callback != null) callback.openSettings();
            return true;
        }
        if (page == Page.KEYPAD) return handleKeypadTouch(x, y);
        if (page == Page.RECENTS) return handleRecentTouch(x, y);
        if (page == Page.CONTACTS || page == Page.FAVORITES) return handleContactTouch(x, y);
        return true;
    }

    private boolean handleKeypadTouch(float x, float y) {
        float boxY = dp(118);
        if (number.length() > 0 && x > width - dp(82) && y > boxY && y < boxY + dp(78)) {
            number.deleteCharAt(number.length() - 1);
            invalidate();
            return true;
        }
        float centerX = width / 2f;
        float colGap = dp(112);
        float startX = centerX - colGap;
        float startY = boxY + dp(117);
        float rowGap = dp(96);
        float r = dp(42);
        for (int i = 0; i < keypad.length; i++) {
            int col = i % 3;
            int row = i / 3;
            float cx = startX + col * colGap;
            float cy = startY + row * rowGap;
            if (distance(x, y, cx, cy) <= r) {
                number.append(keypad[i][0]);
                invalidate();
                return true;
            }
        }
        float callY = startY + rowGap * 4f - dp(4);
        if (distance(x, y, centerX, callY) <= dp(48) && number.length() > 0) {
            if (callback != null) callback.placeCall(number.toString());
            return true;
        }
        return true;
    }

    private boolean handleRecentTouch(float x, float y) {
        if (x < width - dp(82)) return true;
        float rowStart = dp(104) + dp(64) + dp(58) + dp(16);
        float rowH = dp(69);
        int i = (int)((y - rowStart) / rowH);
        if (i >= 0 && i < recents.size() && i < 7 && callback != null) callback.placeCall(recents.get(i).number);
        return true;
    }

    private boolean handleContactTouch(float x, float y) {
        if (x < width - dp(82)) return true;
        float start = page == Page.CONTACTS ? dp(200) : dp(168);
        float row = page == Page.CONTACTS ? dp(68) : dp(80);
        int i = (int)((y - start) / row);
        if (i >= 0 && i < contacts.size() && callback != null) callback.placeCall(contacts.get(i).number);
        return true;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    private float dp(float value) { return value * density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
