package com.mobai.alert.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Minimal XLSX loader tailored for normalized trading history workbooks.
 */
@Component
public class BitLanglangTradeHistoryLoader {

    private static final String SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final LocalDateTime EXCEL_EPOCH = LocalDateTime.of(1899, 12, 30, 0, 0);

    public List<BitLanglangTradeRecord> load(Path workbookPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(workbookPath.toFile(), StandardCharsets.UTF_8)) {
            List<String> sharedStrings = loadSharedStrings(zipFile);
            String firstWorksheet = resolveFirstWorksheet(zipFile);
            List<List<String>> rows = loadRows(zipFile, firstWorksheet, sharedStrings);
            if (rows.isEmpty()) {
                return List.of();
            }
            return toTradeRecords(rows);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse workbook: " + workbookPath, e);
        }
    }

    private List<BitLanglangTradeRecord> toTradeRecords(List<List<String>> rows) {
        Map<String, Integer> headerIndex = buildHeaderIndex(rows.get(0));
        List<BitLanglangTradeRecord> records = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String symbol = cell(row, headerIndex, "交易对");
            String sideText = cell(row, headerIndex, "方向");
            String leverage = cell(row, headerIndex, "杠杆倍数");
            String buyTime = cell(row, headerIndex, "买入时间");
            String sellTime = cell(row, headerIndex, "卖出时间");
            String entryPrice = cell(row, headerIndex, "开仓均价");
            String exitPrice = cell(row, headerIndex, "平仓均价");
            String returnRate = cell(row, headerIndex, "收益率");
            String profitUsd = cell(row, headerIndex, "收益 (USDT)");
            String tradeValue = cell(row, headerIndex, "交易额 (USD)");

            if (!StringUtils.hasText(symbol)
                    || !StringUtils.hasText(sideText)
                    || !StringUtils.hasText(buyTime)
                    || !StringUtils.hasText(sellTime)
                    || !StringUtils.hasText(entryPrice)
                    || !StringUtils.hasText(exitPrice)) {
                continue;
            }

            BitLanglangTradeSide side = parseSide(sideText);
            if (side == null) {
                continue;
            }

            records.add(new BitLanglangTradeRecord(
                    i,
                    symbol,
                    normalizeSymbol(symbol),
                    side,
                    decimal(leverage),
                    excelDateTime(buyTime),
                    excelDateTime(sellTime),
                    decimal(entryPrice),
                    decimal(exitPrice),
                    decimal(returnRate),
                    decimal(profitUsd),
                    decimal(tradeValue)
            ));
        }
        return records;
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headerRow) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String header = headerRow.get(i);
            if (StringUtils.hasText(header)) {
                index.putIfAbsent(header.trim(), i);
            }
        }
        return index;
    }

    private String cell(List<String> row, Map<String, Integer> headerIndex, String header) {
        Integer index = headerIndex.get(header);
        if (index == null || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    private BitLanglangTradeSide parseSide(String value) {
        String normalized = value.trim();
        if ("多".equals(normalized)) {
            return BitLanglangTradeSide.LONG;
        }
        if ("空".equals(normalized)) {
            return BitLanglangTradeSide.SHORT;
        }
        return null;
    }

    private String normalizeSymbol(String sourceSymbol) {
        return sourceSymbol.replace("-", "")
                .replace("_", "")
                .replace("SWAP", "")
                .replace("PERP", "")
                .toUpperCase(Locale.ROOT);
    }

    private LocalDateTime excelDateTime(String serialValue) {
        BigDecimal serial = decimal(serialValue);
        long seconds = serial.multiply(new BigDecimal("86400"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return EXCEL_EPOCH.plusSeconds(seconds);
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private List<String> loadSharedStrings(ZipFile zipFile) throws Exception {
        String xml = readEntry(zipFile, "xl/sharedStrings.xml");
        if (!StringUtils.hasText(xml)) {
            return List.of();
        }

        Document document = parseXml(xml);
        NodeList sharedNodes = document.getElementsByTagNameNS(SHEET_NS, "si");
        List<String> values = new ArrayList<>(sharedNodes.getLength());
        for (int i = 0; i < sharedNodes.getLength(); i++) {
            Element element = (Element) sharedNodes.item(i);
            NodeList textNodes = element.getElementsByTagNameNS(SHEET_NS, "t");
            StringBuilder builder = new StringBuilder();
            for (int j = 0; j < textNodes.getLength(); j++) {
                builder.append(textNodes.item(j).getTextContent());
            }
            values.add(builder.toString());
        }
        return values;
    }

    private String resolveFirstWorksheet(ZipFile zipFile) throws Exception {
        Document workbook = parseXml(readEntry(zipFile, "xl/workbook.xml"));
        Document relationships = parseXml(readEntry(zipFile, "xl/_rels/workbook.xml.rels"));

        NodeList sheetNodes = workbook.getElementsByTagNameNS(SHEET_NS, "sheet");
        if (sheetNodes.getLength() == 0) {
            throw new IOException("Workbook does not contain any sheets");
        }
        Element firstSheet = (Element) sheetNodes.item(0);
        String relationId = firstSheet.getAttributeNS(REL_NS, "id");
        if (!StringUtils.hasText(relationId)) {
            relationId = firstSheet.getAttribute("r:id");
        }

        NodeList relationNodes = relationships.getElementsByTagNameNS(PKG_REL_NS, "Relationship");
        for (int i = 0; i < relationNodes.getLength(); i++) {
            Element relation = (Element) relationNodes.item(i);
            if (relationId.equals(relation.getAttribute("Id"))) {
                String target = relation.getAttribute("Target");
                if (target.startsWith("/")) {
                    return target.substring(1);
                }
                if (target.startsWith("xl/")) {
                    return target;
                }
                return "xl/" + target;
            }
        }
        throw new IOException("Failed to resolve worksheet relation: " + relationId);
    }

    private List<List<String>> loadRows(ZipFile zipFile, String worksheetPath, List<String> sharedStrings) throws Exception {
        Document worksheet = parseXml(readEntry(zipFile, worksheetPath));
        NodeList rowNodes = worksheet.getElementsByTagNameNS(SHEET_NS, "row");
        List<List<String>> rows = new ArrayList<>(rowNodes.getLength());
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            NodeList cellNodes = row.getElementsByTagNameNS(SHEET_NS, "c");
            List<String> values = new ArrayList<>();
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element cell = (Element) cellNodes.item(j);
                int columnIndex = columnIndex(cell.getAttribute("r"));
                while (values.size() <= columnIndex) {
                    values.add("");
                }
                values.set(columnIndex, cellValue(cell, sharedStrings));
            }
            rows.add(values);
        }
        return rows;
    }

    private String cellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList inlineTextNodes = cell.getElementsByTagNameNS(SHEET_NS, "t");
            return inlineTextNodes.getLength() == 0 ? "" : inlineTextNodes.item(0).getTextContent();
        }

        NodeList valueNodes = cell.getElementsByTagNameNS(SHEET_NS, "v");
        if (valueNodes.getLength() == 0) {
            return "";
        }
        String rawValue = valueNodes.item(0).getTextContent();
        if ("s".equals(type)) {
            int index = Integer.parseInt(rawValue);
            if (index >= 0 && index < sharedStrings.size()) {
                return sharedStrings.get(index);
            }
            return "";
        }
        return rawValue;
    }

    private int columnIndex(String reference) {
        if (!StringUtils.hasText(reference)) {
            return 0;
        }
        int result = 0;
        for (int i = 0; i < reference.length(); i++) {
            char ch = reference.charAt(i);
            if (!Character.isLetter(ch)) {
                break;
            }
            result = result * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return Math.max(0, result - 1);
    }

    private String readEntry(ZipFile zipFile, String entryName) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            return null;
        }
        return new String(zipFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    }

    private Document parseXml(String xml) throws Exception {
        if (!StringUtils.hasText(xml)) {
            throw new IOException("Workbook XML entry is empty");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
            // Keep parsing even if the underlying XML implementation does not expose these flags.
        }
        try (Reader reader = new StringReader(xml)) {
            return factory.newDocumentBuilder().parse(new InputSource(reader));
        }
    }
}
