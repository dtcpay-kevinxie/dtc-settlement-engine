package top.dtc.settlement.handler;

import org.springframework.stereotype.Service;
import top.dtc.common.constant.DateTime;
import top.dtc.common.enums.CryptoTransactionType;
import top.dtc.common.pdf.processor.PdfProcessor;
import top.dtc.common.pdf.processor.Table;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.risk.model.KycWalletAddress;

@Service
public class PdfGenerator {

    public byte[] toCryptoReceipt(CryptoTransaction txn, String owner, KycWalletAddress walletAddress, String clientName, String clientContact) {
        PdfProcessor pdf = PdfProcessor.create();
        boolean isDeposit = false;
        if (txn.type == CryptoTransactionType.DEPOSIT || txn.type == CryptoTransactionType.SATOSHI) {
            isDeposit = true;
        }
        this.pdfHeader(pdf, txn, txn.clientId, clientName, clientContact, isDeposit);
        Table itemTable = pdf.table(new float[] {0.08f, 0.17f, 0.16f, 0.13f, 0.46f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Item", 8f)
                .appendBlueCell(isDeposit ? "Deposit Amount" : "Withdrawal Amount", 8f)
                .appendBlueCell(isDeposit ? "Deposit Fees" : "Withdrawal Fees", 8f)
                .appendBlueCell("Net Amount", 8f)
                .appendBlueCell("Transaction hash", 8f);
        itemTable
                .appendCell(txn.currency)
                .appendCell(txn.amount)
                .appendCell(txn.transactionFee)
                .appendCell(txn.transactionFee == null ? txn.amount : txn.amount.subtract(txn.transactionFee))
                .appendCell(txn.txnHash);
        pdf.append(itemTable);

        pdf.append(pdf.table(new float[] {0.32f, 0.68f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell(isDeposit ? "Sender's Crypto Wallet Information": "Beneficiary's Crypto Wallet Information", 8f, 2)
                .appendCell(isDeposit ? "Sender’s Name": "Beneficiary's Name")
                .appendCell(owner)
                .appendCell(isDeposit ? "Sender’s Crypto Wallet Address": "Beneficiary’s Crypto Wallet Address")
                .appendCell(walletAddress.address)
                .appendCell(isDeposit ? "Sender’s Crypto Wallet Network": "Beneficiary's Crypto Wallet Network")
                .appendCell(walletAddress.mainNet.desc)
        );
        return pdf.toByteArray();
    }

    private void pdfHeader(PdfProcessor pdf, CryptoTransaction txn, Long clientId, String clientName, String clientContact, Boolean isDeposit) {
        pdf.append(pdf.table(new float[] {0.15f, 0.87f})
                .fontSize(9)
                .appendBlueCell("Order ID")
                .appendCell(txn.id)
                .appendBlueCell("Order Type")
                .appendCell(isDeposit ? "Crypto Deposit" : "Crypto Withdrawal")
        ).append(pdf.table(new float[] {0.18f, 0.37f, 0.18f, 0.37f})
                .marginTop(7)
                .fontSize(8)
                .appendCell("Client ID", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientId)
                .appendCell("Order Date", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(txn.requestTimestamp.format(DateTime.FORMAT.READABLE_DATE))
                .appendCell("Client Name", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientName)
                .appendCell("Email", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientContact)
        );
    }

}