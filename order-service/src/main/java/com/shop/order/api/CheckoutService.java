package com.shop.order.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.events.Events;
import com.shop.events.Topics;
import com.shop.order.domain.Order;
import com.shop.order.domain.ProductPrice;
import com.shop.order.repo.OrderRepository;
import com.shop.order.repo.ProductPriceRepository;
import com.shop.outbox.OutboxService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CheckoutService {

    /** created=true -> 202 (new order); created=false -> 200 (idempotent replay). */
    public record Result(CheckoutResponse response, boolean created) {}

    private final OrderRepository orders;
    private final ProductPriceRepository priceRepo;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public CheckoutService(OrderRepository orders, ProductPriceRepository priceRepo, OutboxService outbox,
                           TransactionTemplate tx, ObjectMapper mapper) {
        this.orders = orders;
        this.priceRepo = priceRepo;
        this.outbox = outbox;
        this.tx = tx;
        this.mapper = mapper;
    }

    /** Deliberately not @Transactional: a concurrent duplicate surfaces as a constraint violation we can recover from. */
    public Result checkout(UUID customerId, String idempotencyKey, CheckoutRequest request) {
        String hash = hash(request);
        try {
            return tx.execute(s -> doCheckout(customerId, idempotencyKey, hash, request));
        } catch (DataIntegrityViolationException race) {
            // Two identical requests raced; the loser hits UNIQUE(customer_id, idempotency_key). Replay the winner.
            Result replay = tx.execute(s -> orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                    .map(o -> replay(o, hash)).orElse(null));
            if (replay == null) throw race;
            return replay;
        }
    }

    private Result doCheckout(UUID customerId, String key, String hash, CheckoutRequest req) {
        var existing = orders.findByCustomerIdAndIdempotencyKey(customerId, key);
        if (existing.isPresent()) return replay(existing.get(), hash);

        // Merge duplicate SKU lines, then price everything server-side.
        Map<String, Integer> quantities = req.items().stream()
                .collect(Collectors.toMap(CheckoutRequest.Item::sku, CheckoutRequest.Item::quantity, Integer::sum));
        Map<String, ProductPrice> prices = priceRepo.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductPrice::getSku, Function.identity()));

        Order order = new Order(customerId, key, hash, req.shippingAddressId(),
                req.payment().paymentMethodToken(), req.payment().currency());
        for (var entry : quantities.entrySet()) {
            ProductPrice price = prices.get(entry.getKey());
            if (price == null) throw new ApiExceptions.UnprocessableOrder("Unknown SKU " + entry.getKey());
            if (!price.getCurrency().equals(req.payment().currency())) {
                throw new ApiExceptions.UnprocessableOrder(
                        "SKU " + entry.getKey() + " is priced in " + price.getCurrency());
            }
            order.addItem(entry.getKey(), entry.getValue(), price.getUnitPrice());
        }
        orders.saveAndFlush(order);   // flush now so a duplicate-key race fails here, inside the tx

        List<Events.OrderLine> lines = order.getItems().stream()
                .map(i -> new Events.OrderLine(i.getSku(), i.getQuantity(), i.getUnitPrice())).toList();
        outbox.enqueue("Order", Topics.ORDER_EVENTS, order.getId().toString(), Topics.Types.ORDER_CREATED,
                new Events.OrderCreated(order.getId(), customerId, order.getTotalAmount(), order.getCurrency(), lines));

        return new Result(CheckoutResponse.from(order), true);
    }

    private Result replay(Order existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) throw new ApiExceptions.IdempotencyConflict();
        return new Result(CheckoutResponse.from(existing), false);
    }

    private String hash(CheckoutRequest request) {
        try {
            byte[] json = mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
