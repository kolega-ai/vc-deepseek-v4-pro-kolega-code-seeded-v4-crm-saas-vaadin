package com.crmops.channel.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small building blocks shared by every view. */
public final class Ui {

    private Ui() {
    }

    /* ---------- badges ---------- */

    public static Span badge(String label, String variant) {
        Span badge = new Span(label);
        badge.addClassName("badge");
        badge.addClassName("badge-" + variant);
        badge.getElement().setAttribute("aria-label", label);
        return badge;
    }

    public static Span statusBadge(String value, String label) {
        return switch (value) {
            case "ACTIVE", "CLOSED_WON", "EXCELLENT", "SUCCESS", "SENT", "GOOD", "RESOLVED" ->
                    badge(label, "success");
            case "TRIAL", "WAITING_ON_CUSTOMER", "WAITING_ON_INTERNAL", "RENEWAL_PENDING", "AT_RISK",
                 "OPEN", "LEAD", "QUALIFIED", "PENDING", "INFO" -> badge(label, "warning");
            case "SUSPENDED", "CLOSED_LOST", "CRITICAL", "SEV1", "FAILED", "CHURNED", "REVOKED", "EXPIRED",
                 "OVERDUE" -> badge(label, "danger");
            case "SEV2" -> badge(label, "accent");
            case "PROPOSAL", "NEGOTIATION", "SEV3", "CLOSED", "DISABLED" -> badge(label, "info");
            default -> badge(label, "neutral");
        };
    }

    public static Span planBadge(String planCode) {
        return switch (planCode) {
            case "ENTERPRISE" -> badge("Enterprise", "primary");
            case "PRO" -> badge("Pro", "info");
            default -> badge("Starter", "neutral");
        };
    }

    public static Span severityBadge(String severity) {
        String label = switch (severity) {
            case "SEV1" -> "SEV1 · Critical";
            case "SEV2" -> "SEV2 · High";
            default -> "SEV3 · Normal";
        };
        return statusBadge(severity, label);
    }

    public static Span dealStageBadge(String stage) {
        String label = switch (stage) {
            case "LEAD" -> "Lead";
            case "QUALIFIED" -> "Qualified";
            case "PROPOSAL" -> "Proposal";
            case "NEGOTIATION" -> "Negotiation";
            case "CLOSED_WON" -> "Closed won";
            case "CLOSED_LOST" -> "Closed lost";
            default -> "Renewal pending";
        };
        return statusBadge(stage, label);
    }

    public static Span visibilityBadge(String visibility) {
        return switch (visibility) {
            case "INTERNAL" -> badge("Internal", "accent");
            case "BILLING_ONLY" -> badge("Billing only", "warning");
            default -> badge("Customer visible", "success");
        };
    }

    public static Span healthBadge(int score) {
        String bucket = score >= 80 ? "EXCELLENT" : score >= 65 ? "GOOD" : score >= 45 ? "AT_RISK" : "CRITICAL";
        String label = score + " · " + switch (bucket) {
            case "EXCELLENT" -> "Excellent";
            case "GOOD" -> "Good";
            case "AT_RISK" -> "At risk";
            default -> "Critical";
        };
        return statusBadge(bucket, label);
    }

    /* ---------- layout pieces ---------- */

    public static Div statCard(String label, String value, String hint, String tone) {
        Div card = new Div();
        card.addClassName("stat-card");
        if (tone != null) {
            card.addClassName(tone);
        }
        card.add(new Div(new Text(label)) {{
            addClassName("stat-label");
        }});
        card.add(new Div(new Text(value)) {{
            addClassName("stat-value");
        }});
        if (hint != null && !hint.isBlank()) {
            card.add(new Div(new Text(hint)) {{
                addClassName("stat-hint");
            }});
        }
        return card;
    }

    public static Div statGrid(Component... cards) {
        Div grid = new Div();
        grid.addClassName("stat-grid");
        grid.add(cards);
        return grid;
    }

    public static Div card(Component... children) {
        Div card = new Div();
        card.addClassName("card");
        card.add(children);
        return card;
    }

    public static Div pageHeader(String title, String subtitle, Component... actions) {
        Div header = new Div();
        header.addClassName("page-header");
        Div titles = new Div();
        H3 h = new H3(title);
        h.addClassName("page-title");
        titles.add(h);
        if (subtitle != null && !subtitle.isBlank()) {
            Paragraph p = new Paragraph(subtitle);
            p.addClassName("page-subtitle");
            titles.add(p);
        }
        header.add(titles);
        if (actions.length > 0) {
            HorizontalLayout actionBar = new HorizontalLayout();
            actionBar.addClassName("header-actions");
            actionBar.add(actions);
            header.add(actionBar);
        }
        return header;
    }

    public static VerticalLayout emptyState(VaadinIcon icon, String title, String hint) {
        VerticalLayout layout = new VerticalLayout();
        layout.addClassName("empty-state");
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        Icon i = icon.create();
        layout.add(i);
        if (title != null) {
            Paragraph t = new Paragraph(title);
            t.addClassName("empty-title");
            layout.add(t);
        }
        if (hint != null) {
            Paragraph h = new Paragraph(hint);
            h.addClassName("small");
            layout.add(h);
        }
        return layout;
    }

    public static Div emptyGridContent(VaadinIcon icon, String title, String hint) {
        Div wrapper = new Div();
        wrapper.addClassName("grid-empty");
        wrapper.add(emptyState(icon, title, hint));
        return wrapper;
    }

    /* ---------- confirm dialog ---------- */

    public static Dialog confirmDialog(String title, String message, String confirmLabel, Runnable onConfirm) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        Paragraph text = new Paragraph(message);
        text.addClassName("small");
        Button cancel = new Button("Cancel", event -> dialog.close());
        cancel.addClassName("ghost-button");
        Button confirm = new Button(confirmLabel, event -> {
            dialog.close();
            onConfirm.run();
        });
        confirm.addClassName("danger-button");
        HorizontalLayout footer = new HorizontalLayout(cancel, confirm);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        dialog.add(text, footer);
        dialog.open();
        return dialog;
    }

    /* ---------- notifications ---------- */

    public static void success(String message) {
        Notification.show(message, 3200, Notification.Position.BOTTOM_END)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        Notification.show(message, 6000, Notification.Position.BOTTOM_END)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /* ---------- formatting ---------- */

    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm");

    public static String money(BigDecimal value) {
        return value == null ? "—" : MONEY.format(value);
    }

    public static String date(LocalDate value) {
        return value == null ? "—" : DATE.format(value);
    }

    public static String dateTime(Instant value) {
        return value == null ? "—" : DATE_TIME.format(value.atZone(ZoneId.systemDefault()));
    }

    public static String relative(Instant value) {
        if (value == null) {
            return "—";
        }
        Duration duration = Duration.between(value, Instant.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = duration.toDays();
        if (days < 60) {
            return days + "d ago";
        }
        return DATE.format(value.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    /** SLA age with breach styling. */
    public static Span ageBadge(Instant createdAt, Instant slaDueAt, boolean breached) {
        String age = relative(createdAt);
        if (breached) {
            Span s = badge("SLA breached · " + age, "danger");
            s.getElement().setAttribute("title", "due " + (slaDueAt == null ? "—" : dateTime(slaDueAt)));
            return s;
        }
        return badge(age, "neutral");
    }

    public static Select<String> select(String label, String... options) {
        Select<String> select = new Select<>();
        select.setLabel(label);
        select.setItems(options);
        return select;
    }

    public static Button primary(String label) {
        Button button = new Button(label);
        button.addClassName("primary-button");
        return button;
    }

    public static Button ghost(String label) {
        Button button = new Button(label);
        button.addClassName("ghost-button");
        return button;
    }

    public static Button iconButton(VaadinIcon icon, String ariaLabel) {
        Button button = new Button(icon.create());
        button.getElement().setAttribute("aria-label", ariaLabel);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return button;
    }

    /* ---------- Vaadin 24.8 file-handling API helpers ---------- */

    /**
     * Builds a download anchor backed by a {@code DownloadHandler}. The
     * supplier runs on EVERY download request, so re-authorization happens
     * per request even for a long-lived anchor element.
     */
    public static com.vaadin.flow.component.html.Anchor downloadAnchor(
            String label, String className,
            java.util.function.Supplier<com.vaadin.flow.server.streams.DownloadResponse> supplier) {
        com.vaadin.flow.server.streams.DownloadHandler handler =
                com.vaadin.flow.server.streams.DownloadHandler.fromInputStream(event -> supplier.get());
        com.vaadin.flow.component.html.Anchor anchor =
                new com.vaadin.flow.component.html.Anchor(handler, label);
        if (className != null) {
            anchor.addClassName(className);
        }
        anchor.getElement().setAttribute("download", true);
        return anchor;
    }

    /** In-memory upload component using the 24.8 UploadHandler API. */
    public static com.vaadin.flow.component.upload.Upload inMemoryUpload(
            int maxBytes,
            java.util.function.BiConsumer<com.vaadin.flow.server.streams.UploadMetadata, byte[]> onComplete) {
        com.vaadin.flow.server.streams.UploadHandler handler =
                com.vaadin.flow.server.streams.UploadHandler.inMemory((metadata, data) -> {
                    onComplete.accept(metadata, data);
                });
        com.vaadin.flow.component.upload.Upload upload =
                new com.vaadin.flow.component.upload.Upload(handler);
        upload.setMaxFiles(1);
        upload.setMaxFileSize(maxBytes);
        return upload;
    }
}
