package ch.famfrehner.bonflow.printing;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import ch.famfrehner.bonflow.masterdata.Article;
import ch.famfrehner.bonflow.masterdata.ArticleRepository;
import ch.famfrehner.bonflow.masterdata.DiningTableRepository;
import ch.famfrehner.bonflow.masterdata.Modifier;
import ch.famfrehner.bonflow.masterdata.ModifierGroup;
import ch.famfrehner.bonflow.masterdata.ModifierGroupRepository;
import ch.famfrehner.bonflow.masterdata.Printer;
import ch.famfrehner.bonflow.masterdata.PrinterProtocol;
import ch.famfrehner.bonflow.masterdata.PrinterRepository;
import ch.famfrehner.bonflow.masterdata.Station;
import ch.famfrehner.bonflow.masterdata.StationRepository;
import ch.famfrehner.bonflow.ordering.OrderService;
import ch.famfrehner.bonflow.ordering.OrderView;
import ch.famfrehner.bonflow.routing.TicketService;
import ch.famfrehner.bonflow.routing.TicketSummary;
import ch.famfrehner.bonflow.users.WaiterService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns station tickets into print jobs and tracks their delivery. */
@Service
class PrintJobService {

    private static final int BATCH = 50;

    /** A job that is due, with everything needed to send it outside of a transaction. */
    record DueJob(long id, long printerId, String host, int port, byte[] payload) {
    }

    private final PrintJobRepository jobs;
    private final OrderService orders;
    private final TicketService tickets;
    private final StationRepository stations;
    private final PrinterRepository printerRepository;
    private final ArticleRepository articles;
    private final ModifierGroupRepository modifierGroups;
    private final DiningTableRepository tables;
    private final WaiterService waiters;
    private final PrintingProperties props;
    private final Clock clock;

    PrintJobService(PrintJobRepository jobs, OrderService orders, TicketService tickets, StationRepository stations,
            PrinterRepository printerRepository,
            ArticleRepository articles, ModifierGroupRepository modifierGroups, DiningTableRepository tables,
            WaiterService waiters, PrintingProperties props, Clock clock) {
        this.jobs = jobs;
        this.orders = orders;
        this.tickets = tickets;
        this.stations = stations;
        this.printerRepository = printerRepository;
        this.articles = articles;
        this.modifierGroups = modifierGroups;
        this.tables = tables;
        this.waiters = waiters;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Renders the given tickets and queues one job per enabled printer of the ticket's station.
     * Tickets that already have jobs are skipped, so a re-delivered event does not print twice.
     *
     * @return number of jobs created
     */
    @Transactional
    int createJobs(UUID orderId, List<Long> ticketIds) {
        OrderView order = orders.get(orderId);
        List<TicketSummary> wanted = tickets.ticketsOfOrder(orderId).stream()
                .filter(t -> ticketIds.contains(t.id()))
                .filter(t -> !jobs.existsByTicketId(t.id()))
                .toList();
        if (wanted.isEmpty()) {
            return 0;
        }
        Map<Long, Article> articleById = new HashMap<>();
        articles.findAllById(order.items().stream().map(OrderView.ItemView::articleId).collect(
                java.util.stream.Collectors.toSet())).forEach(a -> articleById.put(a.getId(), a));
        Map<Long, String> modifierName = new HashMap<>();
        for (ModifierGroup group : modifierGroups.findAll()) {
            for (Modifier m : group.getModifiers()) {
                modifierName.put(m.getId(), m.getName());
            }
        }
        String tableLabel = tables.findById(order.tableId()).map(t -> t.getLabel()).orElse("?");
        String waiterName = waiters.displayName(order.waiterId()).orElse("?");

        int created = 0;
        for (TicketSummary ticket : wanted) {
            Station station = stations.findById(ticket.stationId()).orElseThrow();
            List<TicketContent.Line> lines = new ArrayList<>();
            for (OrderView.ItemView item : order.items()) {
                if (!ticket.itemIds().contains(item.id())) {
                    continue;
                }
                Article article = articleById.get(item.articleId());
                String name = article == null ? "Artikel " + item.articleId()
                        : article.getShortName() != null && !article.getShortName().isBlank()
                                ? article.getShortName() : article.getName();
                lines.add(new TicketContent.Line(item.quantity(), name, item.course(),
                        item.modifierIds().stream().sorted()
                                .map(id -> modifierName.getOrDefault(id, "?")).toList(),
                        item.note()));
            }
            TicketContent content = new TicketContent(ticket.kind(), station.getName(), tableLabel, waiterName,
                    ticket.createdAt(), order.note(), lines);
            for (Printer printer : station.getPrinters()) {
                if (!printer.isEnabled() || printer.getProtocol() != PrinterProtocol.ESC_POS) {
                    continue;
                }
                byte[] payload = TicketRenderer.render(content, columns(printer), props.zone());
                jobs.save(new PrintJob(ticket.id(), printer.getId(), payload, clock.instant()));
                created++;
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    List<DueJob> dueJobs() {
        List<DueJob> due = new ArrayList<>();
        Map<Long, Printer> printers = new HashMap<>();
        for (PrintJob job : jobs.findDue(clock.instant(), PageRequest.of(0, BATCH))) {
            Printer printer = printers.computeIfAbsent(job.getPrinterId(), id -> printerRepository.findById(id).orElse(null));
            if (printer != null && printer.isEnabled()) {
                due.add(new DueJob(job.getId(), printer.getId(), printer.getHost(), printer.getPort(),
                        job.getPayload()));
            }
        }
        return due;
    }

    @Transactional
    void markSent(long jobId) {
        PrintJob job = jobs.findById(jobId).orElseThrow();
        job.markSent(clock.instant());
        tickets.markPrinted(job.getTicketId());
    }

    @Transactional
    void markAttemptFailed(long jobId, String error) {
        jobs.findById(jobId).orElseThrow().markAttemptFailed(error, clock.instant());
    }

    private static int columns(Printer printer) {
        return printer.getPaperWidthMm() <= 58 ? 32 : 48;
    }
}
