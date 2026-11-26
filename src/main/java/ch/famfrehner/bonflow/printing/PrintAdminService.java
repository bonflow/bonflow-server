package ch.famfrehner.bonflow.printing;

import java.io.IOException;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import ch.famfrehner.bonflow.masterdata.Printer;
import ch.famfrehner.bonflow.masterdata.PrinterRepository;
import ch.famfrehner.bonflow.routing.TicketService;
import ch.famfrehner.bonflow.routing.TicketSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operations for the admin UI: test print, printer status, retry and reprint. */
@Service
public class PrintAdminService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final PrinterRepository printers;
    private final PrintJobRepository jobs;
    private final PrintJobService jobService;
    private final TicketService tickets;
    private final PrinterGateway gateway;
    private final PrintingProperties props;
    private final Clock clock;

    PrintAdminService(PrinterRepository printers, PrintJobRepository jobs, PrintJobService jobService,
            TicketService tickets, PrinterGateway gateway, PrintingProperties props, Clock clock) {
        this.printers = printers;
        this.jobs = jobs;
        this.jobService = jobService;
        this.tickets = tickets;
        this.gateway = gateway;
        this.props = props;
        this.clock = clock;
    }

    /** Prints a short test slip right now and reports whether the printer accepted it. */
    public TestPrintResult testPrint(long printerId) {
        Printer printer = printers.findById(printerId)
                .orElseThrow(() -> new NoSuchElementException("Unknown printer " + printerId));
        int columns = printer.getPaperWidthMm() <= 58 ? 32 : 48;
        byte[] payload = new EscPosBuilder().init()
                .align(EscPosBuilder.Align.CENTER).bold(true).size(2, 2).line("TESTDRUCK")
                .size(1, 1).bold(false)
                .line(printer.getName())
                .line(printer.getHost() + ":" + printer.getPort())
                .line(STAMP.format(clock.instant().atZone(props.zone())))
                .line("-".repeat(columns))
                .line("Wenn Sie das lesen,")
                .line("funktioniert der Drucker.")
                .feed(3).cut().build();
        try {
            gateway.send(printer.getHost(), printer.getPort(), payload);
            return new TestPrintResult(true, "Testdruck gesendet");
        } catch (IOException e) {
            return new TestPrintResult(false, "Drucker nicht erreichbar: " + e.getMessage());
        }
    }

    /** Status of every printer, offline ones first. */
    @Transactional(readOnly = true)
    public List<PrinterStatus> printerStatuses() {
        var pendingByPrinter = jobs.findAllByStatus(PrintJobStatus.PENDING).stream()
                .collect(Collectors.groupingBy(PrintJob::getPrinterId));
        return printers.findAll().stream()
                .map(p -> {
                    List<PrintJob> pending = pendingByPrinter.getOrDefault(p.getId(), List.of());
                    List<PrintJob> failing = pending.stream().filter(j -> j.getAttempts() > 0).toList();
                    String lastError = failing.stream().max(Comparator.comparing(PrintJob::getNextAttemptAt))
                            .map(PrintJob::getLastError).orElse(null);
                    return new PrinterStatus(p.getId(), p.getName(), p.getHost(), p.getPort(), p.isEnabled(),
                            pending.size(), failing.size(), lastError,
                            pending.stream().map(PrintJob::getCreatedAt).min(Comparator.naturalOrder())
                                    .orElse(null));
                })
                .sorted(Comparator.comparing(PrinterStatus::offline).reversed()
                        .thenComparing(PrinterStatus::name))
                .toList();
    }

    /** @return true when at least one printer has tickets it could not print */
    @Transactional(readOnly = true)
    public boolean anyPrinterOffline() {
        return printerStatuses().stream().anyMatch(PrinterStatus::offline);
    }

    /** Skips the backoff: the next worker run tries all pending jobs of the printer again. */
    @Transactional
    public int retryNow(long printerId) {
        List<PrintJob> pending = jobs.findAllByStatus(PrintJobStatus.PENDING).stream()
                .filter(j -> j.getPrinterId() == printerId).toList();
        pending.forEach(j -> j.retryNow(clock.instant()));
        return pending.size();
    }

    /**
     * Prints a ticket again on the printers of its station, marked as "NACHDRUCK".
     *
     * @return number of jobs queued
     */
    @Transactional
    public int reprintTicket(long ticketId) {
        TicketSummary ticket = tickets.find(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Unknown ticket " + ticketId));
        return jobService.createJobs(ticket.orderId(), List.of(ticketId), true);
    }
}
