package ru.zahaand.lifesync.domain.user;

public record UserProfile(
        String displayName,
        String timezone,
        String locale,
        String telegramChatId
) {

    public UserProfile {
        if (timezone == null) {
            timezone = "UTC";
        }
        if (locale == null) {
            locale = "en";
        }
    }

    public UserProfile withDisplayName(String displayName) {
        return new UserProfile(displayName, this.timezone, this.locale, this.telegramChatId);
    }

    public UserProfile withTimezone(String timezone) {
        return new UserProfile(this.displayName, timezone, this.locale, this.telegramChatId);
    }

    public UserProfile withLocale(String locale) {
        return new UserProfile(this.displayName, this.timezone, locale, this.telegramChatId);
    }

    public UserProfile withTelegramChatId(String telegramChatId) {
        return new UserProfile(this.displayName, this.timezone, this.locale, telegramChatId);
    }
}
