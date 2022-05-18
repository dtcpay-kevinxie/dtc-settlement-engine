package top.dtc.settlement.module.aletapay.service;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.dtc.data.core.model.PaymentTransaction;
import top.dtc.data.core.service.PaymentTransactionService;
import top.dtc.data.finance.enums.ReceivableStatus;
import top.dtc.data.finance.model.PayoutReconcile;
import top.dtc.data.finance.model.Receivable;
import top.dtc.data.finance.service.PayoutReconcileService;
import top.dtc.data.finance.service.ReceivableService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.constant.SettlementConstant;
import top.dtc.settlement.exception.ReceivableException;
import top.dtc.settlement.handler.XlsxHandler;
import top.dtc.settlement.module.aletapay.core.properties.AletaProperties;
import top.dtc.settlement.module.aletapay.model.AletaSettlementReport;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

@Log4j2
@Service
public class AletaReconcileService {

    @Autowired
    private PayoutReconcileService payoutReconcileService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private PaymentTransactionService transactionService;

    @Autowired
    private AletaProperties aletaProperties;

    public boolean reconcile(MultipartFile multipartFile, Long receivableId) {
        String referenceNo = multipartFile.getOriginalFilename();
        Receivable receivable = receivableService.getById(receivableId);
        if (receivable == null) {
            throw new ReceivableException(ErrorMessage.RECEIVABLE.INVALID_RECEIVABLE_REF(referenceNo));
        }
        Path path = null;
        try {
            path = Paths.get(
                    aletaProperties.localPath,
                    SettlementConstant.FOLDER.NEW,
                    System.currentTimeMillis() + "_" + referenceNo
            );
            path.getParent().toFile().mkdirs();
            multipartFile.transferTo(path);
            File file = path.toFile();
            Workbook workbook = WorkbookFactory.create(file);
            Sheet sheet = workbook.getSheetAt(0);
            AletaSettlementReport aletaSettlementReport = new AletaSettlementReport();
            aletaSettlementReport.records = XlsxHandler.readListFromSheet(sheet, AletaSettlementReport.Record.class, 5);
            this.processReceivableReconcile(aletaSettlementReport, receivable);
            this.moveToDoneFolder(file);
            return true;
        } catch (Exception e) {
            log.error("aleta readFile failed", e);
            if (path != null) {
                this.moveToFailedFolder(path.toFile());
            }
            return false;
        }
    }

    private void processReceivableReconcile(AletaSettlementReport aletaSettlementReport, Receivable receivable) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (AletaSettlementReport.Record record : aletaSettlementReport.records) {
            if (record.orderId == null || record.num == null) {
                continue;
            }
            PaymentTransaction transaction = transactionService.getById(Long.valueOf(record.orderId));
            if (transaction == null) {
                log.warn("Undefined transactionId {}", record.orderId);
                continue;
            }
            BigDecimal receivedAmount = new BigDecimal(record.settlementAmount);
            PayoutReconcile payoutReconcile = payoutReconcileService.getById(transaction.id);
            payoutReconcile.receivableId = receivable.id;
            payoutReconcile.receivedAmount = receivedAmount;
            payoutReconcile.receivedCurrency = record.settlementCurrency;
            payoutReconcileService.updateById(payoutReconcile);
            totalAmount = totalAmount.add(receivedAmount);
        }
        if (receivable.receivedAmount.setScale(2, RoundingMode.HALF_UP).compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) == 0) {
            receivable.status = ReceivableStatus.RECEIVED;
            receivable.writeOffDate = LocalDate.now();
        } else {
            receivable.status = ReceivableStatus.PARTIAL;
        }
        receivableService.updateById(receivable);
    }

    private void moveToDoneFolder(File file) {
        Path path = Paths.get(
                aletaProperties.localPath,
                SettlementConstant.FOLDER.DONE,
                System.currentTimeMillis() + "_" + file.getName()
        );
        path.getParent().toFile().mkdirs();
        file.renameTo(path.toFile());
    }

    private void moveToFailedFolder(File file) {
        Path path = Paths.get(
                aletaProperties.localPath,
                SettlementConstant.FOLDER.FAILED,
                System.currentTimeMillis() + "_" + file.getName()
        );
        path.getParent().toFile().mkdirs();
        file.renameTo(path.toFile());
    }

}
