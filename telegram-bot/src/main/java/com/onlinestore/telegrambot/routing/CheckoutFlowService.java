package com.onlinestore.telegrambot.routing;

import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.BackendAuthenticationRequiredException;
import com.onlinestore.telegrambot.integration.dto.address.AddressDto;
import com.onlinestore.telegrambot.integration.dto.address.CreateAddressRequest;
import com.onlinestore.telegrambot.integration.dto.cart.CartDto;
import com.onlinestore.telegrambot.integration.dto.cart.CartItemDto;
import com.onlinestore.telegrambot.integration.dto.orders.CreateOrderRequest;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.integration.dto.orders.OrderItemRequest;
import com.onlinestore.telegrambot.integration.service.AddressIntegrationService;
import com.onlinestore.telegrambot.integration.service.CartIntegrationService;
import com.onlinestore.telegrambot.integration.service.OrdersIntegrationService;
import com.onlinestore.telegrambot.session.UserSession;
import com.onlinestore.telegrambot.session.UserSessionService;
import com.onlinestore.telegrambot.session.UserState;
import com.onlinestore.telegrambot.support.PendingWriteGuardService;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Service
@RequiredArgsConstructor
public class CheckoutFlowService {

    private static final String CALLBACK_PREFIX = "checkout:";
    private static final String START_CALLBACK = CALLBACK_PREFIX + "start";
    private static final String ADDRESS_NEW_CALLBACK = CALLBACK_PREFIX + "address:new";
    private static final String ADDRESS_CHANGE_CALLBACK = CALLBACK_PREFIX + "address:change";
    private static final String ADDRESS_SELECT_PREFIX = CALLBACK_PREFIX + "address:select:";
    private static final String CONFIRM_CALLBACK = CALLBACK_PREFIX + "confirm";

    private static final String ATTRIBUTE_ADDRESS_STEP = "checkoutAddressStep";
    private static final String ATTRIBUTE_SELECTED_ADDRESS_ID = "checkoutSelectedAddressId";
    private static final String ATTRIBUTE_CART_SNAPSHOT = "checkoutCartSnapshot";
    private static final String ATTRIBUTE_COUNTRY = "checkoutCountry";
    private static final String ATTRIBUTE_CITY = "checkoutCity";
    private static final String ATTRIBUTE_STREET = "checkoutStreet";
    private static final String ATTRIBUTE_BUILDING = "checkoutBuilding";
    private static final String ATTRIBUTE_APARTMENT = "checkoutApartment";
    private static final String ATTRIBUTE_ADDRESS_SUBMISSION_STATE = "checkoutAddressSubmissionState";
    private static final String ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT = "checkoutAddressSubmissionFingerprint";
    private static final String ATTRIBUTE_ADDRESS_SUBMISSION_STARTED_AT = "checkoutAddressSubmissionStartedAt";
    private static final String ATTRIBUTE_ORDER_SUBMISSION_STATE = "checkoutOrderSubmissionState";
    private static final String ATTRIBUTE_ORDER_SUBMISSION_KEY = "checkoutOrderSubmissionKey";
    private static final String ATTRIBUTE_ORDER_SUBMISSION_STARTED_AT = "checkoutOrderSubmissionStartedAt";

    private static final String STEP_COUNTRY = "country";
    private static final String STEP_CITY = "city";
    private static final String STEP_STREET = "street";
    private static final String STEP_BUILDING = "building";
    private static final String STEP_APARTMENT = "apartment";
    private static final String STEP_POSTAL_CODE = "postalCode";
    private static final String STEP_SELECT = "select";
    private static final String SUBMISSION_PENDING = "pending";
    private static final String ADDRESS_SUBMISSION_GUARD_SCOPE = "address-submit";
    private static final String ORDER_SUBMISSION_GUARD_SCOPE = "order-submit";

    private final CartIntegrationService cartIntegrationService;
    private final AddressIntegrationService addressIntegrationService;
    private final OrdersIntegrationService ordersIntegrationService;
    private final UserSessionService userSessionService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final PendingWriteGuardService pendingWriteGuardService;

    public BotApiMethod<?> startCheckout(BotUpdateContext updateContext, UserSession userSession, String source) {
        return startCheckout(updateContext, userSession, source, null);
    }

    public BotApiMethod<?> handleCallback(BotUpdateContext updateContext, UserSession userSession) {
        String callbackData = updateContext.callbackData().orElse("");
        if (CONFIRM_CALLBACK.equals(callbackData)
            && pendingWriteGuardService.isPending(ORDER_SUBMISSION_GUARD_SCOPE, updateContext.getUserId())) {
            return sendOrEdit(updateContext, orderSubmissionInProgressView());
        }
        if (START_CALLBACK.equals(callbackData)) {
            return startCheckout(updateContext, userSession, "callback:checkout:start");
        }
        if (ADDRESS_NEW_CALLBACK.equals(callbackData)) {
            return startNewAddressEntry(updateContext, userSession, "callback:checkout:address:new", null);
        }
        if (ADDRESS_CHANGE_CALLBACK.equals(callbackData)) {
            return startCheckout(
                updateContext,
                userSession,
                "callback:checkout:address:change",
                "Choose another shipping address for this order."
            );
        }
        if (callbackData.startsWith(ADDRESS_SELECT_PREFIX)) {
            Long addressId = safeParseLong(callbackData.substring(ADDRESS_SELECT_PREFIX.length()));
            if (addressId == null) {
                return unavailableAction(updateContext, "Unknown address selection.");
            }
            return selectAddress(updateContext, userSession, addressId);
        }
        if (CONFIRM_CALLBACK.equals(callbackData)) {
            if (!isConfirmationActive(userSession)) {
                return sendOrEdit(updateContext, checkoutAlreadyProcessedView());
            }
            return confirmCheckout(updateContext, userSession);
        }
        return unavailableAction(updateContext, "Unknown checkout action.");
    }

    public BotApiMethod<?> handleAddressInput(BotUpdateContext updateContext, UserSession userSession) {
        String step = userSession.getAttributes().get(ATTRIBUTE_ADDRESS_STEP);
        String input = updateContext.messageText().orElse("").trim();

        if (!StringUtils.hasText(step) || STEP_SELECT.equals(step)) {
            return telegramMessageFactory.message(
                updateContext.getChatId(),
                new BotView(
                    "Use the inline buttons to choose a saved address or tap Add new address.",
                    telegramMessageFactory.mainMenuKeyboard()
                )
            );
        }

        return switch (step) {
            case STEP_COUNTRY -> captureAddressField(
                updateContext,
                userSession,
                ATTRIBUTE_COUNTRY,
                STEP_CITY,
                input,
                validateCountry(input),
                "Now send the city for delivery."
            );
            case STEP_CITY -> captureAddressField(
                updateContext,
                userSession,
                ATTRIBUTE_CITY,
                STEP_STREET,
                input,
                validateRequiredLength(input, 100, "Send the city name (up to 100 characters)."),
                "Now send the street line."
            );
            case STEP_STREET -> captureAddressField(
                updateContext,
                userSession,
                ATTRIBUTE_STREET,
                STEP_BUILDING,
                input,
                validateRequiredLength(input, 255, "Send the street line (up to 255 characters)."),
                "Send the building or house number.\nSend - to skip."
            );
            case STEP_BUILDING -> captureOptionalField(
                updateContext,
                userSession,
                ATTRIBUTE_BUILDING,
                STEP_APARTMENT,
                input,
                50,
                "Send the apartment, suite, or unit.\nSend - to skip."
            );
            case STEP_APARTMENT -> captureOptionalField(
                updateContext,
                userSession,
                ATTRIBUTE_APARTMENT,
                STEP_POSTAL_CODE,
                input,
                50,
                "Finally, send the postal code."
            );
            case STEP_POSTAL_CODE -> completeAddressEntry(updateContext, userSession, input);
            default -> telegramMessageFactory.menuMessage(
                updateContext.getChatId(),
                "Checkout lost its address step. Please open /cart and start checkout again."
            );
        };
    }

    private BotApiMethod<?> startCheckout(
        BotUpdateContext updateContext,
        UserSession userSession,
        String source,
        String hint
    ) {
        if (pendingWriteGuardService.isPending(ORDER_SUBMISSION_GUARD_SCOPE, updateContext.getUserId())
            || isPendingOrderSubmission(userSession)) {
            return sendOrEdit(updateContext, orderSubmissionInProgressView());
        }
        try {
            CartDto cart = cartIntegrationService.getCart(updateContext.getUserId());
            if (isEmpty(cart)) {
                return sendOrEdit(updateContext, emptyCartView(
                    hint == null ? "Your cart is empty. Add items before checkout." : hint
                ));
            }

            List<AddressDto> addresses = addressIntegrationService.getAddresses(updateContext.getUserId());
            if (!addresses.isEmpty()) {
                pendingWriteGuardService.clearPending(ADDRESS_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
            }
            if (addresses.isEmpty()) {
                if (pendingWriteGuardService.isPending(ADDRESS_SUBMISSION_GUARD_SCOPE, updateContext.getUserId())
                    || isPendingAddressSubmission(userSession)) {
                    return sendOrEdit(updateContext, addressSubmissionInProgressView());
                }
                return beginAddressEntry(updateContext, userSession, source, cart, hint);
            }

            UserSession selectionSession = userSessionService.transitionTo(
                userSession,
                updateContext.getChatId(),
                UserState.ENTERING_ADDRESS,
                source
            );
            userSessionService.rememberInputs(selectionSession, updateContext.getChatId(), attributes(
                ATTRIBUTE_ADDRESS_STEP, STEP_SELECT,
                ATTRIBUTE_SELECTED_ADDRESS_ID, null,
                ATTRIBUTE_CART_SNAPSHOT, cartSnapshot(cart),
                ATTRIBUTE_COUNTRY, null,
                ATTRIBUTE_CITY, null,
                ATTRIBUTE_STREET, null,
                ATTRIBUTE_BUILDING, null,
                ATTRIBUTE_APARTMENT, null,
                ATTRIBUTE_ADDRESS_SUBMISSION_STATE, null,
                ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT, null,
                ATTRIBUTE_ADDRESS_SUBMISSION_STARTED_AT, null,
                ATTRIBUTE_ORDER_SUBMISSION_STATE, null,
                ATTRIBUTE_ORDER_SUBMISSION_KEY, null,
                ATTRIBUTE_ORDER_SUBMISSION_STARTED_AT, null
            ));

            return sendOrEdit(updateContext, new BotView(
                buildAddressSelectionText(addresses, hint),
                addressSelectionKeyboard(addresses)
            ));
        } catch (BackendAuthenticationRequiredException | BackendApiException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> startNewAddressEntry(
        BotUpdateContext updateContext,
        UserSession userSession,
        String source,
        String hint
    ) {
        try {
            CartDto cart = cartIntegrationService.getCart(updateContext.getUserId());
            if (isEmpty(cart)) {
                return sendOrEdit(updateContext, emptyCartView("Your cart is empty. Add items before checkout."));
            }
            return beginAddressEntry(updateContext, userSession, source, cart, hint);
        } catch (BackendAuthenticationRequiredException | BackendApiException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> beginAddressEntry(
        BotUpdateContext updateContext,
        UserSession userSession,
        String source,
        CartDto cart,
        String hint
    ) {
        UserSession addressSession = userSessionService.transitionTo(
            userSession,
            updateContext.getChatId(),
            UserState.ENTERING_ADDRESS,
            source
        );
        userSessionService.rememberInputs(addressSession, updateContext.getChatId(), attributes(
            ATTRIBUTE_ADDRESS_STEP, STEP_COUNTRY,
            ATTRIBUTE_SELECTED_ADDRESS_ID, null,
            ATTRIBUTE_CART_SNAPSHOT, cartSnapshot(cart),
            ATTRIBUTE_COUNTRY, null,
            ATTRIBUTE_CITY, null,
            ATTRIBUTE_STREET, null,
            ATTRIBUTE_BUILDING, null,
            ATTRIBUTE_APARTMENT, null,
            ATTRIBUTE_ADDRESS_SUBMISSION_STATE, null,
            ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT, null,
            ATTRIBUTE_ORDER_SUBMISSION_STATE, null,
            ATTRIBUTE_ORDER_SUBMISSION_KEY, null
        ));
        return sendOrEdit(updateContext, addressPromptView(hint));
    }

    private BotApiMethod<?> selectAddress(BotUpdateContext updateContext, UserSession userSession, Long addressId) {
        try {
            CartDto cart = cartIntegrationService.getCart(updateContext.getUserId());
            if (isEmpty(cart)) {
                return sendOrEdit(updateContext, emptyCartView("Your cart is empty. Add items before checkout."));
            }

            AddressDto address = addressIntegrationService.getAddresses(updateContext.getUserId()).stream()
                .filter(candidate -> addressId.equals(candidate.id()))
                .findFirst()
                .orElse(null);
            if (address == null) {
                return startCheckout(
                    updateContext,
                    userSession,
                    "callback:checkout:address:change",
                    "That saved address is no longer available. Choose another address or add a new one."
                );
            }
            return showConfirmation(
                updateContext,
                userSession,
                cart,
                address,
                "callback:checkout:address:select",
                null
            );
        } catch (BackendAuthenticationRequiredException | BackendApiException ex) {
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> captureAddressField(
        BotUpdateContext updateContext,
        UserSession userSession,
        String attributeKey,
        String nextStep,
        String input,
        String validationError,
        String nextPrompt
    ) {
        if (validationError != null) {
            return promptMessage(updateContext.getChatId(), validationError);
        }

        userSessionService.rememberInputs(userSession, updateContext.getChatId(), attributes(
            attributeKey, input,
            ATTRIBUTE_ADDRESS_STEP, nextStep
        ));
        return promptMessage(updateContext.getChatId(), nextPrompt);
    }

    private BotApiMethod<?> captureOptionalField(
        BotUpdateContext updateContext,
        UserSession userSession,
        String attributeKey,
        String nextStep,
        String input,
        int maxLength,
        String nextPrompt
    ) {
        String normalized = normalizeOptionalInput(input);
        if (normalized != null && normalized.length() > maxLength) {
            return promptMessage(
                updateContext.getChatId(),
                "This field is too long. Keep it within " + maxLength + " characters or send - to skip."
            );
        }

        userSessionService.rememberInputs(userSession, updateContext.getChatId(), attributes(
            attributeKey, normalized,
            ATTRIBUTE_ADDRESS_STEP, nextStep
        ));
        return promptMessage(updateContext.getChatId(), nextPrompt);
    }

    private BotApiMethod<?> completeAddressEntry(BotUpdateContext updateContext, UserSession userSession, String input) {
        String validationError = validateRequiredLength(input, 20, "Send the postal code (up to 20 characters).");
        if (validationError != null) {
            return promptMessage(updateContext.getChatId(), validationError);
        }

        String addressFingerprint = addressFingerprint(userSession, input);
        if (isPendingAddressSubmission(userSession)
            && addressFingerprint.equals(userSession.getAttributes().get(ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT))) {
            return startCheckout(
                updateContext,
                userSession,
                "callback:checkout:address:resume",
                "The last address submission is already being finalized. If it was saved, choose it below to continue."
            );
        }

        UserSession addressSubmittingSession = userSessionService.rememberInputs(
            userSession,
            updateContext.getChatId(),
            attributes(
                ATTRIBUTE_ADDRESS_SUBMISSION_STATE, SUBMISSION_PENDING,
                ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT, addressFingerprint,
                ATTRIBUTE_ADDRESS_SUBMISSION_STARTED_AT, submissionStartedAt()
            )
        );
        pendingWriteGuardService.markPending(ADDRESS_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());

        try {
            CreateAddressRequest request = new CreateAddressRequest(
                null,
                addressSubmittingSession.getAttributes().get(ATTRIBUTE_COUNTRY),
                addressSubmittingSession.getAttributes().get(ATTRIBUTE_CITY),
                addressSubmittingSession.getAttributes().get(ATTRIBUTE_STREET),
                addressSubmittingSession.getAttributes().get(ATTRIBUTE_BUILDING),
                addressSubmittingSession.getAttributes().get(ATTRIBUTE_APARTMENT),
                input,
                false
            );
            AddressDto address = addressIntegrationService.createAddress(updateContext.getUserId(), request);
            try {
                CartDto cart = cartIntegrationService.getCart(updateContext.getUserId());
                if (isEmpty(cart)) {
                    pendingWriteGuardService.clearPending(ADDRESS_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
                    clearAddressSubmission(addressSubmittingSession, updateContext.getChatId());
                    return sendOrEdit(
                        updateContext,
                        emptyCartView("Your address was saved, but your cart is empty now. Add items before checkout.")
                    );
                }
                return showConfirmation(
                    updateContext,
                    addressSubmittingSession,
                    cart,
                    address,
                    "checkout:address:complete",
                    "Review your order before placing it."
                );
            } catch (BackendAuthenticationRequiredException | BackendApiException ex) {
                pendingWriteGuardService.clearPending(ADDRESS_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
                return startCheckout(
                    updateContext,
                    addressSubmittingSession,
                    "callback:checkout:address:resume",
                    "Your address was saved. Choose it below to continue checkout."
                );
            }
        } catch (BackendAuthenticationRequiredException ex) {
            pendingWriteGuardService.clearPending(ADDRESS_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
            clearAddressSubmission(addressSubmittingSession, updateContext.getChatId());
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        } catch (BackendApiException ex) {
            if (isUncertainWriteFailure(ex)) {
                return startCheckout(
                    updateContext,
                    addressSubmittingSession,
                    "callback:checkout:address:resume",
                    "The address submission may have completed already. If it was saved, choose it below to continue."
                );
            }
            pendingWriteGuardService.clearPending(ADDRESS_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
            clearAddressSubmission(addressSubmittingSession, updateContext.getChatId());
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> confirmCheckout(BotUpdateContext updateContext, UserSession userSession) {
        try {
            CartDto cart = cartIntegrationService.getCart(updateContext.getUserId());
            if (isEmpty(cart)) {
                return sendOrEdit(updateContext, emptyCartView("Your cart is empty now. Add items before checkout."));
            }

            AddressDto address = resolveSelectedAddress(updateContext.getUserId(), userSession);
            if (address == null) {
                return startCheckout(
                    updateContext,
                    userSession,
                    "callback:checkout:address:change",
                    "The selected shipping address is no longer available. Choose another address."
                );
            }

            String currentSnapshot = cartSnapshot(cart);
            String storedSnapshot = userSession.getAttributes().get(ATTRIBUTE_CART_SNAPSHOT);
            if (!currentSnapshot.equals(storedSnapshot)) {
                return sendOrEdit(updateContext, cartChangedView());
            }

            String orderSubmissionKey = currentSnapshot + "|" + address.id();
            UserSession pendingOrderSession = userSessionService.rememberInputs(
                userSession,
                updateContext.getChatId(),
                attributes(
                    ATTRIBUTE_ORDER_SUBMISSION_STATE, SUBMISSION_PENDING,
                    ATTRIBUTE_ORDER_SUBMISSION_KEY, orderSubmissionKey,
                    ATTRIBUTE_ORDER_SUBMISSION_STARTED_AT, submissionStartedAt()
                )
            );
            pendingWriteGuardService.markPending(ORDER_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());

            OrderDto order = ordersIntegrationService.createOrder(
                updateContext.getUserId(),
                new CreateOrderRequest(address.id(), toOrderItems(cart), null)
            );

            UserSession trackingSession = userSessionService.transitionTo(
                pendingOrderSession,
                updateContext.getChatId(),
                UserState.TRACKING_ORDER,
                "checkout:confirm"
            );
            pendingWriteGuardService.clearPending(ORDER_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
            UserSession clearedTrackingSession = clearOrderSubmission(trackingSession, updateContext.getChatId());
            userSessionService.rememberInput(
                clearedTrackingSession,
                updateContext.getChatId(),
                "orderReference",
                order.id() == null ? "" : Long.toString(order.id())
            );

            return sendOrEdit(updateContext, new BotView(buildOrderConfirmationText(order), orderConfirmationKeyboard()));
        } catch (BackendAuthenticationRequiredException ex) {
            pendingWriteGuardService.clearPending(ORDER_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
            clearOrderSubmission(userSession, updateContext.getChatId());
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        } catch (BackendApiException ex) {
            if ("ADDRESS_ACCESS_DENIED".equals(ex.getErrorCode())) {
                pendingWriteGuardService.clearPending(ORDER_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
                clearOrderSubmission(userSession, updateContext.getChatId());
                return startCheckout(
                    updateContext,
                    userSession,
                    "callback:checkout:address:change",
                    "The selected shipping address is no longer available. Choose another address."
                );
            }
            if (isRecoverableCheckoutFailure(ex)) {
                pendingWriteGuardService.clearPending(ORDER_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
                clearOrderSubmission(userSession, updateContext.getChatId());
                return sendOrEdit(updateContext, cartChangedView());
            }
            if (isUncertainWriteFailure(ex)) {
                return sendOrEdit(updateContext, orderSubmissionInProgressView());
            }
            pendingWriteGuardService.clearPending(ORDER_SUBMISSION_GUARD_SCOPE, updateContext.getUserId());
            clearOrderSubmission(userSession, updateContext.getChatId());
            return sendOrEdit(updateContext, new BotView(ex.getMessage(), telegramMessageFactory.mainMenuKeyboard()));
        }
    }

    private BotApiMethod<?> showConfirmation(
        BotUpdateContext updateContext,
        UserSession userSession,
        CartDto cart,
        AddressDto address,
        String source,
        String hint
    ) {
        UserSession confirmationSession = userSessionService.transitionTo(
            userSession,
            updateContext.getChatId(),
            UserState.CONFIRMING_ORDER,
            source
        );
        userSessionService.rememberInputs(confirmationSession, updateContext.getChatId(), attributes(
            ATTRIBUTE_ADDRESS_STEP, null,
            ATTRIBUTE_SELECTED_ADDRESS_ID, address.id() == null ? null : address.id().toString(),
            ATTRIBUTE_CART_SNAPSHOT, cartSnapshot(cart),
            ATTRIBUTE_COUNTRY, null,
            ATTRIBUTE_CITY, null,
            ATTRIBUTE_STREET, null,
            ATTRIBUTE_BUILDING, null,
            ATTRIBUTE_APARTMENT, null,
            ATTRIBUTE_ADDRESS_SUBMISSION_STATE, null,
            ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT, null,
            ATTRIBUTE_ADDRESS_SUBMISSION_STARTED_AT, null,
            ATTRIBUTE_ORDER_SUBMISSION_STATE, null,
            ATTRIBUTE_ORDER_SUBMISSION_KEY, null,
            ATTRIBUTE_ORDER_SUBMISSION_STARTED_AT, null
        ));
        return sendOrEdit(updateContext, new BotView(buildConfirmationText(cart, address, hint), confirmationKeyboard()));
    }

    private AddressDto resolveSelectedAddress(Long telegramUserId, UserSession userSession) {
        Long selectedAddressId = safeParseLong(userSession.getAttributes().get(ATTRIBUTE_SELECTED_ADDRESS_ID));
        if (selectedAddressId == null) {
            return null;
        }
        return addressIntegrationService.getAddresses(telegramUserId).stream()
            .filter(address -> selectedAddressId.equals(address.id()))
            .findFirst()
            .orElse(null);
    }

    private BotView addressPromptView(String hint) {
        StringBuilder text = new StringBuilder("Checkout is ready.\nSend the country code (2-3 letters, for example US or DEU).");
        if (StringUtils.hasText(hint)) {
            text.append("\n\n").append(hint);
        }
        return new BotView(text.toString(), telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Back to cart", "route:cart"),
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        )));
    }

    private String buildAddressSelectionText(List<AddressDto> addresses, String hint) {
        StringBuilder text = new StringBuilder("Choose a shipping address for this order.");
        if (StringUtils.hasText(hint)) {
            text.append("\n").append(hint);
        }
        for (AddressDto address : addresses) {
            text.append("\n\n- ").append(addressLabel(address))
                .append("\n  ").append(addressInlineSummary(address));
            if (address.isDefault()) {
                text.append("\n  Default address");
            }
        }
        text.append("\n\nTap an address button below or add a new one.");
        return text.toString();
    }

    private InlineKeyboardMarkup addressSelectionKeyboard(List<AddressDto> addresses) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (AddressDto address : addresses) {
            rows.add(new InlineKeyboardRow(
                telegramMessageFactory.callbackButton(addressLabel(address), ADDRESS_SELECT_PREFIX + address.id())
            ));
        }
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Add new address", ADDRESS_NEW_CALLBACK),
            telegramMessageFactory.callbackButton("Back to cart", "route:cart")
        ));
        rows.add(new InlineKeyboardRow(
            telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
        ));
        return telegramMessageFactory.keyboard(rows);
    }

    private String buildConfirmationText(CartDto cart, AddressDto address, String hint) {
        StringBuilder text = new StringBuilder("Review your order");
        if (StringUtils.hasText(hint)) {
            text.append("\n").append(hint);
        }
        text.append("\n\nShipping address:\n").append(addressInlineSummary(address));

        List<CartItemDto> items = cart.items() == null ? List.of() : cart.items();
        text.append("\n\nItems:");
        for (CartItemDto item : items) {
            text.append("\n- ").append(valueOrDash(item.productName()));
            if (StringUtils.hasText(item.variantName())) {
                text.append(" / ").append(item.variantName());
            }
            text.append(" x").append(item.quantity() == null ? 0 : item.quantity())
                .append(" = ")
                .append(formatAmount(item.totalAmount()))
                .append(appendCurrency(item.unitPriceCurrency()));
        }

        text.append("\n\nTotal: ")
            .append(formatAmount(cart.totalAmount()))
            .append(appendCurrency(cart.totalCurrency()))
            .append("\nTap Place order when everything looks correct.");
        return text.toString();
    }

    private InlineKeyboardMarkup confirmationKeyboard() {
        return telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Place order", CONFIRM_CALLBACK)
            ),
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Change address", ADDRESS_CHANGE_CALLBACK),
                telegramMessageFactory.callbackButton("Open cart", "route:cart")
            ),
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        ));
    }

    private BotView emptyCartView(String message) {
        return new BotView(message, telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Catalog", "route:catalog"),
                telegramMessageFactory.callbackButton("Search", "route:search")
            ),
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        )));
    }

    private BotView cartChangedView() {
        return new BotView(
            "Your cart changed while you were checking out.\nOpen cart to review the latest items, then start checkout again.",
            telegramMessageFactory.keyboard(List.of(
                new InlineKeyboardRow(
                    telegramMessageFactory.callbackButton("Open cart", "route:cart"),
                    telegramMessageFactory.callbackButton("Change address", ADDRESS_CHANGE_CALLBACK)
                ),
                new InlineKeyboardRow(
                    telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
                )
            ))
        );
    }

    private BotView checkoutAlreadyProcessedView() {
        return new BotView(
            "This checkout confirmation is no longer active.\nOpen Orders to track the latest order or reopen the cart to start a new checkout.",
            telegramMessageFactory.keyboard(List.of(
                new InlineKeyboardRow(
                    telegramMessageFactory.callbackButton("Orders", "route:order"),
                    telegramMessageFactory.callbackButton("Open cart", "route:cart")
                ),
                new InlineKeyboardRow(
                    telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
                )
            ))
        );
    }

    private BotView orderSubmissionInProgressView() {
        return new BotView(
            "The order submission is already being finalized or may have completed.\nOpen Orders to check the latest status before trying checkout again.",
            telegramMessageFactory.keyboard(List.of(
                new InlineKeyboardRow(
                    telegramMessageFactory.callbackButton("Orders", "route:order"),
                    telegramMessageFactory.callbackButton("Open cart", "route:cart")
                ),
                new InlineKeyboardRow(
                    telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
                )
            ))
        );
    }

    private BotView addressSubmissionInProgressView() {
        return new BotView(
            "The previous address submission is still being finalized or may already be saved.\nRetry checkout in a moment to reload saved addresses before entering it again.",
            telegramMessageFactory.keyboard(List.of(
                new InlineKeyboardRow(
                    telegramMessageFactory.callbackButton("Retry checkout", START_CALLBACK),
                    telegramMessageFactory.callbackButton("Open cart", "route:cart")
                ),
                new InlineKeyboardRow(
                    telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
                )
            ))
        );
    }

    private String buildOrderConfirmationText(OrderDto order) {
        return "Order #" + order.id()
            + " has been placed.\nStatus: " + valueOrDash(order.status())
            + "\nTotal: " + formatAmount(order.totalAmount()) + appendCurrency(order.totalCurrency())
            + "\nUse /order or the Orders menu to track it again.";
    }

    private InlineKeyboardMarkup orderConfirmationKeyboard() {
        return telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Orders", "route:order"),
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        ));
    }

    private List<OrderItemRequest> toOrderItems(CartDto cart) {
        if (cart.items() == null) {
            return List.of();
        }
        return cart.items().stream()
            .map(item -> new OrderItemRequest(item.productVariantId(), item.quantity()))
            .toList();
    }

    private String cartSnapshot(CartDto cart) {
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            return "empty";
        }

        String itemPart = cart.items().stream()
            .map(item -> item.productVariantId() + ":" + item.quantity() + ":" + snapshotAmount(item.totalAmount()))
            .sorted()
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        return itemPart + "|" + snapshotAmount(cart.totalAmount()) + "|" + valueOrDash(cart.totalCurrency());
    }

    private String snapshotAmount(BigDecimal amount) {
        return amount == null ? "-" : amount.toPlainString();
    }

    private boolean isRecoverableCheckoutFailure(BackendApiException ex) {
        Integer statusCode = ex.getStatusCode();
        return "INSUFFICIENT_STOCK".equals(ex.getErrorCode())
            || "OUT_OF_STOCK".equals(ex.getErrorCode())
            || statusCode != null && (statusCode == 404 || statusCode == 409 || statusCode == 422);
    }

    private boolean isEmpty(CartDto cart) {
        return cart == null || cart.items() == null || cart.items().isEmpty();
    }

    private String validateCountry(String input) {
        if (!StringUtils.hasText(input)) {
            return "Send the country code (2-3 letters, for example US or DEU).";
        }
        String normalized = input.trim();
        if (normalized.length() > 3) {
            return "Country code must be 2-3 letters, for example US or DEU.";
        }
        return null;
    }

    private String validateRequiredLength(String input, int maxLength, String guidance) {
        if (!StringUtils.hasText(input)) {
            return guidance;
        }
        if (input.trim().length() > maxLength) {
            return guidance;
        }
        return null;
    }

    private String normalizeOptionalInput(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String normalized = input.trim();
        if ("-".equals(normalized) || "skip".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private boolean isUncertainWriteFailure(BackendApiException ex) {
        Integer statusCode = ex.getStatusCode();
        return statusCode == null || statusCode >= 500 || statusCode == 408 || statusCode == 429;
    }

    private String addressLabel(AddressDto address) {
        if (StringUtils.hasText(address.label())) {
            return address.label();
        }
        return valueOrDash(address.street()) + ", " + valueOrDash(address.city());
    }

    private String addressInlineSummary(AddressDto address) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(address.street())) {
            parts.add(address.street());
        }
        if (StringUtils.hasText(address.building())) {
            parts.add(address.building());
        }
        if (StringUtils.hasText(address.apartment())) {
            parts.add("apt " + address.apartment());
        }
        if (StringUtils.hasText(address.city())) {
            parts.add(address.city());
        }
        if (StringUtils.hasText(address.postalCode())) {
            parts.add(address.postalCode());
        }
        if (StringUtils.hasText(address.country())) {
            parts.add(address.country());
        }
        return String.join(", ", parts);
    }

    private BotApiMethod<?> unavailableAction(BotUpdateContext updateContext, String message) {
        return telegramMessageFactory.callbackNotice(updateContext.callbackQueryId().orElseThrow(), message);
    }

    private BotApiMethod<?> promptMessage(Long chatId, String text) {
        return telegramMessageFactory.message(chatId, new BotView(text, telegramMessageFactory.keyboard(List.of(
            new InlineKeyboardRow(
                telegramMessageFactory.callbackButton("Back to cart", "route:cart"),
                telegramMessageFactory.callbackButton("Main menu", "route:main-menu")
            )
        ))));
    }

    private BotApiMethod<?> sendOrEdit(BotUpdateContext updateContext, BotView botView) {
        Integer messageId = updateContext.messageId().orElse(null);
        if (updateContext.callbackQueryId().isPresent() && messageId != null) {
            return telegramMessageFactory.editMessage(updateContext.getChatId(), messageId, botView);
        }
        return telegramMessageFactory.message(updateContext.getChatId(), botView);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String appendCurrency(String currency) {
        return StringUtils.hasText(currency) ? " " + currency : "";
    }

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private Long safeParseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isConfirmationActive(UserSession userSession) {
        return userSession.getState() == UserState.CONFIRMING_ORDER
            && StringUtils.hasText(userSession.getAttributes().get(ATTRIBUTE_SELECTED_ADDRESS_ID))
            && StringUtils.hasText(userSession.getAttributes().get(ATTRIBUTE_CART_SNAPSHOT));
    }

    private boolean isPendingOrderSubmission(UserSession userSession) {
        return isPendingSubmission(
            userSession,
            ATTRIBUTE_ORDER_SUBMISSION_STATE,
            ATTRIBUTE_ORDER_SUBMISSION_KEY,
            ATTRIBUTE_ORDER_SUBMISSION_STARTED_AT,
            userSession.getAttributes().get(ATTRIBUTE_ORDER_SUBMISSION_KEY)
        );
    }

    private boolean isPendingAddressSubmission(UserSession userSession) {
        return isPendingSubmission(
            userSession,
            ATTRIBUTE_ADDRESS_SUBMISSION_STATE,
            ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT,
            ATTRIBUTE_ADDRESS_SUBMISSION_STARTED_AT,
            userSession.getAttributes().get(ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT)
        );
    }

    private boolean isPendingSubmission(
        UserSession userSession,
        String stateKey,
        String fingerprintKey,
        String startedAtKey,
        String fingerprint
    ) {
        return SUBMISSION_PENDING.equals(userSession.getAttributes().get(stateKey))
            && StringUtils.hasText(fingerprint)
            && fingerprint.equals(userSession.getAttributes().get(fingerprintKey))
            && isSubmissionTimestampFresh(userSession.getAttributes().get(startedAtKey));
    }

    private String addressFingerprint(UserSession userSession, String postalCode) {
        return String.join(
            "|",
            valueOrDash(userSession.getAttributes().get(ATTRIBUTE_COUNTRY)),
            valueOrDash(userSession.getAttributes().get(ATTRIBUTE_CITY)),
            valueOrDash(userSession.getAttributes().get(ATTRIBUTE_STREET)),
            valueOrDash(userSession.getAttributes().get(ATTRIBUTE_BUILDING)),
            valueOrDash(userSession.getAttributes().get(ATTRIBUTE_APARTMENT)),
            valueOrDash(postalCode)
        );
    }

    private void clearAddressSubmission(UserSession userSession, Long chatId) {
        userSessionService.rememberInputs(userSession, chatId, attributes(
            ATTRIBUTE_ADDRESS_SUBMISSION_STATE, null,
            ATTRIBUTE_ADDRESS_SUBMISSION_FINGERPRINT, null,
            ATTRIBUTE_ADDRESS_SUBMISSION_STARTED_AT, null
        ));
    }

    private UserSession clearOrderSubmission(UserSession userSession, Long chatId) {
        return userSessionService.rememberInputs(userSession, chatId, attributes(
            ATTRIBUTE_ORDER_SUBMISSION_STATE, null,
            ATTRIBUTE_ORDER_SUBMISSION_KEY, null,
            ATTRIBUTE_ORDER_SUBMISSION_STARTED_AT, null
        ));
    }

    private String submissionStartedAt() {
        return Long.toString(System.currentTimeMillis());
    }

    private boolean isSubmissionTimestampFresh(String startedAtValue) {
        if (!StringUtils.hasText(startedAtValue)) {
            return false;
        }
        try {
            long startedAtEpochMillis = Long.parseLong(startedAtValue);
            long elapsedMillis = System.currentTimeMillis() - startedAtEpochMillis;
            return elapsedMillis >= 0 && elapsedMillis < pendingWriteGuardService.guardTtl().toMillis();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private Map<String, String> attributes(String... keyValues) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            attributes.put(keyValues[index], keyValues[index + 1]);
        }
        return attributes;
    }
}
