package top.dtc.settlement.handler;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;

public class XlsxUtils {

    public static Cell getCellByPos(Sheet sheet, String position) {
        CellReference cr = new CellReference(position);
        return sheet.getRow(cr.getRow()).getCell(cr.getCol());
    }

}
