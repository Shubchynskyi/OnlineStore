package com.onlinestore.telegrambot.session;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long chatId;

    @Builder.Default
    private UserState state = UserState.MAIN_MENU;

    private String lastCommand;

    @Builder.Default
    private Map<String, String> attributes = new LinkedHashMap<>();

    private Long updatedAtEpochMillis;

    public static UserSession initial(Long userId, Long chatId) {
        return UserSession.builder()
            .userId(userId)
            .chatId(chatId)
            .state(UserState.MAIN_MENU)
            .attributes(new LinkedHashMap<>())
            .updatedAtEpochMillis(System.currentTimeMillis())
            .build();
    }
}
