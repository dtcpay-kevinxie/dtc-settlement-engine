package dtc.top.settlement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateTest {

    public static void main(String[] args) {
        String s = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        System.out.println(s);
    }
}
