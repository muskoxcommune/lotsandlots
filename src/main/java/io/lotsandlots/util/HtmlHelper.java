package io.lotsandlots.util;

public class HtmlHelper {

    public static void appendDataTablesFeatures(StringBuilder htmlBuilder, String tableName, String... features) {
        htmlBuilder.append("<script>");
        htmlBuilder.append("$(document).ready(function() {");
            htmlBuilder.append("$('#").append(tableName).append("').DataTable({");
            for (String feature : features) {
                htmlBuilder.append(feature);
            }
            htmlBuilder.append("});");
        htmlBuilder.append("});");
        htmlBuilder.append("</script>");
    }

    public static void appendDataTablesTags(StringBuilder htmlBuilder) {
        htmlBuilder.append("<link rel=\"stylesheet\" href=\"https://cdn.datatables.net/1.11.3/css/jquery.dataTables.min.css\">");
        htmlBuilder.append("<script src=\"https://code.jquery.com/jquery-3.5.1.js\"></script>");
        htmlBuilder.append("<script src=\"https://cdn.datatables.net/1.11.3/js/jquery.dataTables.min.js\"></script>");
    }

    public static void appendTableHeaderRow(StringBuilder htmlBuilder, String... columnNames) {
        htmlBuilder.append("<thead>");
        htmlBuilder.append("<tr>");
        for (String name : columnNames) {
            htmlBuilder.append("<th>").append(name).append("</th>");
        }
        htmlBuilder.append("</tr>");
        htmlBuilder.append("</thead>");
    }
}
