package com.civictrack.service;

import com.civictrack.model.*;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Excel (.xlsx via Apache POI) and PDF (OpenPDF) exports built from the same live database figures. */
@Service
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class ReportExportService {
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final AnalyticsService analytics;

    @Transactional(readOnly = true)
    public byte[] excel(LocalDate from, LocalDate to, Long departmentId, Category category) throws IOException {
        Map<String, Object> s = analytics.summary(from, to, departmentId, category);
        List<Complaint> list = analytics.filtered(from, to, departmentId, category);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex());
            head.setFont(f); head.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sum = wb.createSheet("Summary");
            String[][] rows = {{"Total complaints", str(s.get("total"))}, {"Pending (submitted)", str(s.get("pending"))},
                    {"Under review", str(s.get("underReview"))}, {"Assigned", str(s.get("assigned"))}, {"In progress", str(s.get("inProgress"))},
                    {"Resolution submitted", str(s.get("resolutionSubmitted"))}, {"Resolved", str(s.get("resolved"))},
                    {"Rejected", str(s.get("rejected"))}, {"Reopened", str(s.get("reopened"))}, {"Overdue (SLA)", str(s.get("overdue"))},
                    {"Resolution rate %", str(s.get("resolutionRate"))}, {"Avg resolution time (hours)", str(s.get("avgResolutionHours"))},
                    {"Average citizen rating", str(s.get("avgRating"))}};
            header(sum, head, "Metric", "Value");
            int r = 1;
            for (String[] row : rows) { Row x = sum.createRow(r++); x.createCell(0).setCellValue(row[0]); x.createCell(1).setCellValue(row[1]); }
            sum.autoSizeColumn(0); sum.autoSizeColumn(1);

            Sheet dep = wb.createSheet("Departments");
            header(dep, head, "Department", "Total", "Resolved", "Open", "Overdue", "Avg resolution (h)");
            r = 1;
            for (Map<String, Object> m : (List<Map<String, Object>>) s.get("departmentStats")) {
                Row x = dep.createRow(r++);
                x.createCell(0).setCellValue(str(m.get("department")));
                num(x, 1, m.get("total")); num(x, 2, m.get("resolved")); num(x, 3, m.get("open")); num(x, 4, m.get("overdue")); num(x, 5, m.get("avgResolutionHours"));
            }
            for (int i = 0; i < 6; i++) dep.autoSizeColumn(i);

            Sheet cat = wb.createSheet("Categories");
            header(cat, head, "Category", "Complaints");
            r = 1;
            for (Map.Entry<String, Long> e : ((Map<String, Long>) s.get("byCategory")).entrySet()) {
                Row x = cat.createRow(r++); x.createCell(0).setCellValue(e.getKey()); x.createCell(1).setCellValue(e.getValue());
            }
            cat.autoSizeColumn(0);

            Sheet off = wb.createSheet("Officer performance");
            header(off, head, "Officer", "Department", "Assigned", "In progress", "Resolved", "Overdue", "Avg resolution (h)");
            r = 1;
            for (Map<String, Object> m : (List<Map<String, Object>>) s.get("officerPerformance")) {
                Row x = off.createRow(r++);
                x.createCell(0).setCellValue(str(m.get("officer"))); x.createCell(1).setCellValue(str(m.get("department")));
                num(x, 2, m.get("assigned")); num(x, 3, m.get("inProgress")); num(x, 4, m.get("resolved")); num(x, 5, m.get("overdue")); num(x, 6, m.get("avgResolutionHours"));
            }
            for (int i = 0; i < 7; i++) off.autoSizeColumn(i);

            Sheet cs = wb.createSheet("Complaints");
            header(cs, head, "Tracking ID", "Title", "Category", "Priority", "Status", "Department", "Officer", "Created", "Resolved", "SLA due", "Address");
            r = 1;
            for (Complaint c : list) {
                Row x = cs.createRow(r++);
                x.createCell(0).setCellValue(c.getTrackingId()); x.createCell(1).setCellValue(c.getTitle());
                x.createCell(2).setCellValue(c.getCategory().name()); x.createCell(3).setCellValue(c.getPriority().name());
                x.createCell(4).setCellValue(c.getStatus().name());
                x.createCell(5).setCellValue(c.getDepartment() == null ? "" : c.getDepartment().getName());
                x.createCell(6).setCellValue(c.getAssignedOfficer() == null ? "" : c.getAssignedOfficer().getName());
                x.createCell(7).setCellValue(fmt(c.getCreatedAt())); x.createCell(8).setCellValue(fmt(c.getResolvedAt()));
                x.createCell(9).setCellValue(fmt(c.getSlaDueAt())); x.createCell(10).setCellValue(c.getAddress() == null ? "" : c.getAddress());
            }
            for (int i = 0; i < 10; i++) cs.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public byte[] pdf(LocalDate from, LocalDate to, Long departmentId, Category category) {
        Map<String, Object> s = analytics.summary(from, to, departmentId, category);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 58, 138));
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(30, 58, 138));
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

        doc.add(new Paragraph("CivicTrack - Complaint Report", h1));
        doc.add(new Paragraph("Generated " + LocalDateTime.now().format(DT) + "   |   Period: " + (from == null ? "all" : from) + " to " + (to == null ? "today" : to)
                + (departmentId != null ? "   |   Department filter applied" : "") + (category != null ? "   |   Category: " + category : ""), small));
        doc.add(new Paragraph(" "));

        doc.add(new Paragraph("Summary", h2));
        PdfPTable t = table(2, new float[]{3, 1});
        hdr(t, "Metric", "Value");
        String[][] rows = {{"Total complaints", "total"}, {"Pending (submitted)", "pending"}, {"Under review", "underReview"}, {"Assigned", "assigned"},
                {"In progress", "inProgress"}, {"Resolution submitted", "resolutionSubmitted"}, {"Resolved", "resolved"}, {"Rejected", "rejected"},
                {"Reopened", "reopened"}, {"Overdue (SLA)", "overdue"}, {"Resolution rate (%)", "resolutionRate"},
                {"Average resolution time (hours)", "avgResolutionHours"}, {"Average citizen rating (1-5)", "avgRating"}};
        for (String[] row : rows) { cell(t, row[0]); cell(t, str(s.get(row[1]))); }
        doc.add(t);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Department-wise statistics", h2));
        PdfPTable d = table(6, new float[]{3, 1, 1, 1, 1, 1.4f});
        hdr(d, "Department", "Total", "Resolved", "Open", "Overdue", "Avg hrs");
        for (Map<String, Object> m : (List<Map<String, Object>>) s.get("departmentStats")) {
            cell(d, str(m.get("department"))); cell(d, str(m.get("total"))); cell(d, str(m.get("resolved")));
            cell(d, str(m.get("open"))); cell(d, str(m.get("overdue"))); cell(d, str(m.get("avgResolutionHours")));
        }
        doc.add(d);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Category-wise statistics", h2));
        PdfPTable c = table(2, new float[]{3, 1});
        hdr(c, "Category", "Complaints");
        for (Map.Entry<String, Long> e : ((Map<String, Long>) s.get("byCategory")).entrySet()) { cell(c, e.getKey()); cell(c, String.valueOf(e.getValue())); }
        doc.add(c);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Officer performance", h2));
        PdfPTable o = table(6, new float[]{2.4f, 2.4f, 1, 1, 1, 1.2f});
        hdr(o, "Officer", "Department", "Assigned", "Resolved", "Overdue", "Avg hrs");
        for (Map<String, Object> m : (List<Map<String, Object>>) s.get("officerPerformance")) {
            cell(o, str(m.get("officer"))); cell(o, str(m.get("department"))); cell(o, str(m.get("assigned")));
            cell(o, str(m.get("resolved"))); cell(o, str(m.get("overdue"))); cell(o, str(m.get("avgResolutionHours")));
        }
        doc.add(o);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Monthly trend", h2));
        PdfPTable mt = table(3, new float[]{2, 1, 1});
        hdr(mt, "Month", "Submitted", "Resolved");
        for (Map<String, Object> m : (List<Map<String, Object>>) s.get("monthly")) { cell(mt, str(m.get("month"))); cell(mt, str(m.get("submitted"))); cell(mt, str(m.get("resolved"))); }
        doc.add(mt);
        doc.close();
        return out.toByteArray();
    }

    // ---- helpers
    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String fmt(LocalDateTime t) { return t == null ? "" : t.format(DT); }
    private static void num(Row r, int col, Object v) { r.createCell(col).setCellValue(v instanceof Number n ? n.doubleValue() : 0); }
    private static void header(Sheet sh, CellStyle style, String... names) {
        Row r = sh.createRow(0);
        for (int i = 0; i < names.length; i++) { Cell c = r.createCell(i); c.setCellValue(names[i]); c.setCellStyle(style); }
    }
    private static PdfPTable table(int cols, float[] widths) {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        try { t.setWidths(widths); } catch (Exception ignored) { }
        return t;
    }
    private static void hdr(PdfPTable t, String... names) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String n : names) { PdfPCell c = new PdfPCell(new Phrase(n, f)); c.setBackgroundColor(new Color(30, 58, 138)); c.setPadding(4); t.addCell(c); }
    }
    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        c.setPadding(4);
        t.addCell(c);
    }
}
