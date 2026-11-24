package io.github.nemios11.cvgenerator.domain.profile;

/**
 * Upper bounds that keep a profile reasonable in size. They protect storage and
 * rendering from abuse while staying far above any realistic CV.
 */
public final class ProfileLimits {

    public static final int NAME_LENGTH = 200;
    public static final int SHORT_TEXT_LENGTH = 300;
    public static final int LONG_TEXT_LENGTH = 5_000;
    public static final int URL_LENGTH = 500;
    public static final int TAG_LENGTH = 100;

    public static final int ENTRIES = 100;
    public static final int BULLETS = 30;
    public static final int TAGS = 60;
    public static final int SKILL_ITEMS = 100;

    private ProfileLimits() {}
}
