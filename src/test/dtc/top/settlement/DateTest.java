package dtc.top.settlement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 14:47<br/>
 */
public class DateTest {

    public static void main(String[] args) {
        String s = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        System.out.println(s);
    }
}
