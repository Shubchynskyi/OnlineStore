package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.session.UserState;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserStateMachine {

    private static final Map<String, UserState> ROUTES = Map.of(
        "main-menu", UserState.MAIN_MENU,
        "catalog", UserState.BROWSING_CATALOG,
        "search", UserState.SEARCHING,
        "cart", UserState.VIEWING_CART,
        "order", UserState.TRACKING_ORDER
    );

    private static final Map<UserState, String> TEXT_INPUT_KEYS = Map.of(
        UserState.SEARCHING, "searchQuery",
        UserState.TRACKING_ORDER, "orderReference",
        UserState.ENTERING_ADDRESS, "shippingAddress",
        UserState.CHATTING_WITH_AI, "assistantPrompt"
    );

    public Optional<UserState> resolveRoute(String route) {
        return Optional.ofNullable(ROUTES.get(route));
    }

    public Optional<String> resolveTextInputKey(UserState userState) {
        return Optional.ofNullable(TEXT_INPUT_KEYS.get(userState));
    }

    public String acknowledgmentFor(UserState userState) {
        return switch (userState) {
            case SEARCHING -> "Search query saved. Product search integration arrives in T-003.";
            case TRACKING_ORDER -> "Order reference saved. Order lookup integration arrives in T-003.";
            case ENTERING_ADDRESS -> "Address saved. Checkout flow arrives in T-005.";
            case CHATTING_WITH_AI -> "Message saved. AI assistant arrives in T-006.";
            default -> "Text input is not expected in the current dialog state.";
        };
    }
}
