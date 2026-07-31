package cafe.bluearchive.installer.shizuku;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

public final class ShellResult implements Parcelable {
    public final int exitCode;
    @NonNull public final String stdout;
    @NonNull public final String stderr;

    public ShellResult(int exitCode, @NonNull String stdout, @NonNull String stderr) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    boolean isSuccess() {
        return exitCode == 0;
    }

    // ── Parcelable ──────────────────────────────────────────────

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(exitCode);
        dest.writeString(stdout);
        dest.writeString(stderr);
    }

    public static final Creator<ShellResult> CREATOR = new Creator<>() {
        @Override
        public ShellResult createFromParcel(Parcel in) {
            return new ShellResult(
                in.readInt(),
                in.readString(),
                in.readString());
        }

        @Override
        public ShellResult[] newArray(int size) {
            return new ShellResult[size];
        }
    };
}
