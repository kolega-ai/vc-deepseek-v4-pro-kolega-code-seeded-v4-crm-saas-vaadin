package com.crmops.channel.ui;

import com.crmops.channel.domain.DocumentMeta;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.DocumentService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadResponse;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Customer portal documents. The service already filters to customer-visible
 * documents; downloads re-authorize on every request inside the anchor
 * supplier.
 */
@Route(value = "portal/documents", layout = MainLayout.class)
@PageTitle("Documents · CrmOps")
@RouteAccess({Role.CUSTOMER_CONTACT})
public class PortalDocumentsView extends VerticalLayout {

    private final DocumentService documentService;
    private final Grid<DocumentMeta> grid = new Grid<>(DocumentMeta.class, false);

    public PortalDocumentsView(DocumentService documentService) {
        this.documentService = documentService;
        addClassName("page");

        add(Ui.pageHeader("Shared documents", "Files your organization has shared with you."));

        add(buildGrid());
        refresh();
    }

    private Div buildGrid() {
        Div wrapper = new Div();
        wrapper.addClassName("grid-card");

        grid.addColumn(DocumentMeta::getName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(doc -> Ui.badge(doc.getCategory().label(), "neutral"))
                .setHeader("Category").setAutoWidth(true);
        grid.addColumn(doc -> humanSize(doc.getSizeBytes()))
                .setHeader("Size").setAutoWidth(true);
        grid.addColumn(doc -> uploadedLabel(doc.getUploadedAt()))
                .setHeader("Uploaded").setAutoWidth(true);
        grid.addComponentColumn(this::downloadLink)
                .setHeader("Download").setAutoWidth(true);

        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.FILE,
                "No documents yet", "Shared documents will appear here."));
        wrapper.add(grid);
        return wrapper;
    }

    private Anchor downloadLink(DocumentMeta doc) {
        return Ui.downloadAnchor("Download", "ghost-button", () -> {
            try {
                DocumentService.Download d = documentService.downloadStream(doc.getId());
                return new DownloadResponse(d.stream(), d.fileName(), d.contentType(), d.sizeBytes());
            } catch (AccessDeniedException e) {
                return DownloadResponse.error(403, "Access denied");
            } catch (Exception e) {
                return DownloadResponse.error(404, "Not found");
            }
        });
    }

    private void refresh() {
        try {
            grid.setItems(documentService.list(SecurityUtils.requireUser().customerOrgId()));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
    }

    private static String uploadedLabel(Instant uploadedAt) {
        return uploadedAt == null ? "—" : Ui.date(uploadedAt.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
