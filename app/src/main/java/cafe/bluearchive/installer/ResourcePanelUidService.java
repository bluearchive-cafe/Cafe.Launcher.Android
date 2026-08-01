package cafe.bluearchive.installer;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResourcePanelUidService {
    static final String SOURCE_AUTO = "auto";
    static final String SOURCE_CUSTOM = "custom";

    private static final Pattern UID_PATTERN = Pattern.compile("^[A-Z]{8}$");
    private static final Pattern UID_COOKIE_PATTERN = Pattern.compile(
            "(?:^|[\\t; ,])uid(?:[\\t =]+)([A-Za-z]{8})(?:$|[\\t; ,])");
    private static final String COOKIE_LIBRARY_PATH =
            "/storage/emulated/0/Android/data/com.YostarJP.BlueArchive/files/Cookies/Library";
    private static final File COOKIE_LIBRARY = new File(COOKIE_LIBRARY_PATH);
    private static final byte[] COOKIE_DOMAIN = "bluearchive.cafe".getBytes();

    private final Context context;

    ResourcePanelUidService(Context context) {
        this.context = context.getApplicationContext();
    }

    UidResult resolve() {
        String source = InstallerPreferences.resourcePanelUidSource(context);
        String manualUid = sanitize(InstallerPreferences.resourcePanelUid(context));
        if (SOURCE_CUSTOM.equals(source)) {
            return isValid(manualUid)
                    ? new UidResult(manualUid, SOURCE_CUSTOM, SourceDetail.SAVED)
                    : new UidResult("", SOURCE_CUSTOM, SourceDetail.NONE);
        }

        UidResult automatic = readAutomaticUid();
        if (automatic.hasValidUid()) {
            return automatic;
        }
        if (isValid(manualUid)) {
            return new UidResult(manualUid, SOURCE_AUTO, SourceDetail.SAVED);
        }
        return new UidResult("", SOURCE_AUTO, SourceDetail.NONE);
    }

    void saveManualUid(String uid) {
        InstallerPreferences.save(context, InstallerPreferences.PREF_RESOURCE_PANEL_UID, sanitize(uid));
    }

    void saveSource(String source) {
        InstallerPreferences.save(context, InstallerPreferences.PREF_RESOURCE_PANEL_UID_SOURCE,
                SOURCE_CUSTOM.equals(source) ? SOURCE_CUSTOM : SOURCE_AUTO);
    }

    static boolean isValid(String uid) {
        return uid != null && UID_PATTERN.matcher(uid).matches();
    }

    static String sanitize(String uid) {
        return uid == null ? "" : uid.trim().toUpperCase(Locale.US);
    }

    private UidResult readAutomaticUid() {
        UidResult direct = readAutomaticUid(SourceDetail.DIRECT_FILE, this::readDirectCookieUid);
        if (direct.hasValidUid()) return direct;

        UidResult shizuku = readAutomaticUid(SourceDetail.SHIZUKU, this::readShizukuCookieUid);
        if (shizuku.hasValidUid()) return shizuku;

        UidResult root = readAutomaticUid(SourceDetail.ROOT, this::readRootCookieUid);
        if (root.hasValidUid()) return root;

        return new UidResult("", SOURCE_AUTO, SourceDetail.NONE);
    }

    private UidResult readAutomaticUid(SourceDetail detail, UidReader reader) {
        String uid = sanitize(reader.read());
        return isValid(uid)
                ? new UidResult(uid, SOURCE_AUTO, detail)
                : new UidResult("", SOURCE_AUTO, SourceDetail.NONE);
    }

    private String readDirectCookieUid() {
        try {
            return parseUid(readAllBytes(COOKIE_LIBRARY));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readShizukuCookieUid() {
        try {
            ShizukuInstallBackend backend = InstallBackendFactory.getOrCreateShizukuBackend();
            ShellExecutor.ShellResult result = backend.executeShell(
                    context, "sh", "-c", "base64 " + shellQuote(COOKIE_LIBRARY_PATH));
            if (!result.isSuccess()) return "";
            String uid = parseBase64Uid(result.out);
            return isValid(uid) ? uid : parseUid(result.out);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readRootCookieUid() {
        try {
            if (!RootInstallBackend.isRootAvailable()) return "";
            RootInstallBackend backend = InstallBackendFactory.getOrCreateRootBackend();
            ShellExecutor.ShellResult result = backend.executeShell(
                    "sh", "-c", "base64 " + shellQuote(COOKIE_LIBRARY_PATH));
            if (!result.isSuccess()) return "";
            String uid = parseBase64Uid(result.out);
            return isValid(uid) ? uid : parseUid(result.out);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String parseBase64Uid(String content) {
        if (content == null || content.isEmpty()) return "";
        try {
            return parseUid(Base64.decode(content, Base64.DEFAULT));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String parseUid(byte[] content) {
        if (content == null || content.length == 0 || !contains(content, COOKIE_DOMAIN, 0)) return "";
        for (int i = 0; i <= content.length - 3; i++) {
            if (!matchesAscii(content, i, "uid")) continue;

            int valueStart = binaryCookieValueStart(content, i);
            if (valueStart < 0) {
                valueStart = textCookieValueStart(content, i + 3);
            }
            String uid = uidAt(content, valueStart);
            if (isValid(uid)) return uid;
        }
        return "";
    }

    private static int binaryCookieValueStart(byte[] content, int nameStart) {
        if (nameStart == 0 || (content[nameStart - 1] & 0xFF) != 3) return -1;
        int lengthIndex = nameStart + 3;
        if (lengthIndex >= content.length || (content[lengthIndex] & 0xFF) != 8) return -1;
        return lengthIndex + 1;
    }

    private static int textCookieValueStart(byte[] content, int index) {
        if (index >= content.length) return -1;
        int current = index;
        while (current < content.length && isTextCookieSeparator(content[current])) {
            current++;
        }
        return current == index ? -1 : current;
    }

    private static boolean isTextCookieSeparator(byte value) {
        return value == '=' || value == '\t' || value == ' ';
    }

    private static String uidAt(byte[] content, int start) {
        if (start < 0 || start + 8 > content.length) return "";
        char[] chars = new char[8];
        for (int i = 0; i < chars.length; i++) {
            int value = content[start + i] & 0xFF;
            if (value >= 'a' && value <= 'z') value -= 32;
            if (value < 'A' || value > 'Z') return "";
            chars[i] = (char) value;
        }
        return new String(chars);
    }

    private static boolean matchesAscii(byte[] content, int offset, String value) {
        if (offset < 0 || offset + value.length() > content.length) return false;
        for (int i = 0; i < value.length(); i++) {
            int actual = content[offset + i] & 0xFF;
            int expected = value.charAt(i);
            if (actual >= 'A' && actual <= 'Z') actual += 32;
            if (actual != expected) return false;
        }
        return true;
    }

    private static boolean contains(byte[] content, byte[] needle, int start) {
        if (needle.length == 0) return true;
        for (int i = Math.max(0, start); i <= content.length - needle.length; i++) {
            boolean found = true;
            for (int j = 0; j < needle.length; j++) {
                int actual = content[i + j] & 0xFF;
                int expected = needle[j] & 0xFF;
                if (actual >= 'A' && actual <= 'Z') actual += 32;
                if (actual != expected) {
                    found = false;
                    break;
                }
            }
            if (found) return true;
        }
        return false;
    }

    private static String parseUid(java.io.Reader reader) throws IOException {
        try (BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            while ((line = buffered.readLine()) != null) {
                String uid = parseUidLine(line);
                if (isValid(uid)) return uid;
            }
        }
        return "";
    }

    private static String parseUid(String content) {
        if (content == null) return "";
        String binaryUid = parseUid(content.getBytes());
        if (isValid(binaryUid)) return binaryUid;

        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String uid = parseUidLine(line);
            if (isValid(uid)) return uid;
        }
        return "";
    }

    private static String parseUidLine(String line) {
        if (line == null) return "";
        String lower = line.toLowerCase(Locale.US);
        if (!lower.contains("bluearchive.cafe") || !lower.contains("uid")) {
            return "";
        }
        Matcher matcher = UID_COOKIE_PATTERN.matcher(line);
        if (matcher.find()) {
            String uid = sanitize(matcher.group(1));
            if (isValid(uid)) return uid;
        }
        return "";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private interface UidReader {
        String read();
    }

    enum SourceDetail {
        NONE,
        DIRECT_FILE,
        SHIZUKU,
        ROOT,
        SAVED
    }

    static final class UidResult {
        final String uid;
        final String source;
        final SourceDetail detail;

        UidResult(String uid, String source, SourceDetail detail) {
            this.uid = uid;
            this.source = source;
            this.detail = detail;
        }

        boolean hasValidUid() {
            return isValid(uid);
        }
    }
}
