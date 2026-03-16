package com.onlinestore.telegrambot.session;

import java.util.Optional;

public interface UserSessionStore {

    Optional<UserSession> findByUserId(Long userId);

    UserSession save(UserSession userSession);

    void deleteByUserId(Long userId);
}
