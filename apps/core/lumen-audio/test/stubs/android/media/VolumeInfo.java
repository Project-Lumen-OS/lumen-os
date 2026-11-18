package android.media;

/** Host stub: the stream and whether an index was given (DspOwned must leave the stored indexes alone). */
public final class VolumeInfo {
    public final int stream;
    public final boolean indexSet;

    VolumeInfo(int stream, boolean indexSet) {
        this.stream = stream;
        this.indexSet = indexSet;
    }

    public static final class Builder {
        private final int stream;
        private boolean index;

        public Builder(int stream) { this.stream = stream; }
        public Builder setVolumeIndex(int i) { index = true; return this; }
        public Builder setMinVolumeIndex(int i) { index = true; return this; }
        public Builder setMaxVolumeIndex(int i) { index = true; return this; }
        public VolumeInfo build() { return new VolumeInfo(stream, index); }
    }
}
