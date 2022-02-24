package top.dtc.settlement.handler;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import top.dtc.common.constant.DateTime;
import top.dtc.common.enums.CryptoTransactionType;
import top.dtc.common.enums.FiatTransactionType;
import top.dtc.common.pdf.processor.PdfProcessor;
import top.dtc.common.pdf.processor.Table;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.model.FiatTransaction;
import top.dtc.data.core.model.Otc;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.RemitInfo;
import top.dtc.data.risk.model.KycWalletAddress;

import java.time.LocalDateTime;

@Service
public class PdfGenerator {

    public byte[] toCryptoReceipt(CryptoTransaction txn, String owner, KycWalletAddress walletAddress, String clientName, String clientContact) {
        PdfProcessor pdf = PdfProcessor.create();
        this.pdfHeader(pdf, txn, txn.clientId, clientName, clientContact);
        Table itemTable = pdf.table(new float[] {0.08f, 0.17f, 0.16f, 0.13f, 0.46f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Item", 8f)
                .appendBlueCell(txn.type == CryptoTransactionType.DEPOSIT ? "Deposit Amount" : "Withdrawal Amount", 8f)
                .appendBlueCell(txn.type == CryptoTransactionType.DEPOSIT ? "Deposit Fees" : "Withdrawal Fees", 8f)
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
                .appendBlueCell(txn.type == CryptoTransactionType.DEPOSIT ? "Sender's Crypto Wallet Information": "Beneficiary's Crypto Wallet Information", 8f, 2)
                .appendCell(txn.type == CryptoTransactionType.DEPOSIT ? "Sender’s Name": "Beneficiary's Name")
                .appendCell(owner)
                .appendCell(txn.type == CryptoTransactionType.DEPOSIT ? "Sender’s Crypto Wallet Address": "Beneficiary’s Crypto Wallet Address")
                .appendCell(walletAddress.address)
                .appendCell(txn.type == CryptoTransactionType.DEPOSIT ? "Sender’s Crypto Wallet Network": "Beneficiary's Crypto Wallet Network")
                .appendCell(walletAddress.mainNet.desc)
        );
        return pdf.toByteArray();
    }
    public ResponseEntity<ByteArrayResource> buildCryptoReceipt(CryptoTransaction txn, String owner, KycWalletAddress walletAddress, String clientName, String clientContact) {
        PdfProcessor pdf = PdfProcessor.create();
        this.pdfHeader(pdf, txn, txn.clientId, clientName, clientContact);
        Table itemTable = pdf.table(new float[] {0.08f, 0.17f, 0.16f, 0.13f, 0.46f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Item", 8f)
                .appendBlueCell(txn.type == CryptoTransactionType.DEPOSIT ? "Deposit Amount" : "Withdrawal Amount", 8f)
                .appendBlueCell(txn.type == CryptoTransactionType.DEPOSIT ? "Deposit Fees" : "Withdrawal Fees", 8f)
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
                .appendBlueCell(txn.type == CryptoTransactionType.DEPOSIT ? "Sender's Crypto Wallet Information": "Beneficiary's Crypto Wallet Information", 8f, 2)
                .appendCell(txn.type == CryptoTransactionType.DEPOSIT ? "Sender’s Name": "Beneficiary's Name")
                .appendCell(owner)
                .appendCell(txn.type == CryptoTransactionType.DEPOSIT ? "Sender’s Crypto Wallet Address": "Beneficiary’s Crypto Wallet Address")
                .appendCell(walletAddress.address)
                .appendCell(txn.type == CryptoTransactionType.DEPOSIT ? "Sender’s Crypto Wallet Network": "Beneficiary's Crypto Wallet Network")
                .appendCell(walletAddress.mainNet.desc)
        );

        if (txn.type == CryptoTransactionType.DEPOSIT || txn.type == CryptoTransactionType.SATOSHI) {
            return pdf.toResponseEntity("Crypto-deposit-" + txn.id);
        } else {
            return pdf.toResponseEntity("Crypto-withdrawal-" + txn.id);
        }
    }

    public byte[] toFiatReceipt(FiatTransaction txn, RemitInfo remitInfo, String clientName, String clientContact) {
        PdfProcessor pdf = PdfProcessor.create();
        this.pdfHeader(pdf, txn, txn.clientId, clientName, clientContact);
        Table itemTable = pdf.table(new float[] {0.08f, 0.17f, 0.16f, 0.13f, 0.46f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Item", 8f)
                .appendBlueCell(txn.type == FiatTransactionType.DEPOSIT ? "Deposit Amount" : "Withdrawal Amount", 8f)
                .appendBlueCell(txn.type == FiatTransactionType.DEPOSIT ? "Deposit Fees" : "Withdrawal Fees", 8f)
                .appendBlueCell("Net Amount", 8f)
                .appendBlueCell("Transaction hash", 8f);
        itemTable
                .appendCell(txn.currency)
                .appendCell(txn.amount)
                .appendCell(txn.transactionFee)
                .appendCell(txn.transactionFee == null ? txn.amount : txn.amount.subtract(txn.transactionFee))
                .appendCell(txn.referenceNo);
        pdf.append(itemTable);

        pdf.append(pdf.table(new float[] {0.32f, 0.68f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender's Bank Information" : "Beneficiary's Bank Information", 8f, 2)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Name" : "Beneficiary's Name")
                .appendCell(clientName)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Address" : "Beneficiary's Address")
                .appendCell(remitInfo.beneficiaryAddress)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Account" : "Beneficiary's Account")
                .appendCell(remitInfo.beneficiaryAccount)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Bank Name" : "Beneficiary's Bank Name")
                .appendCell(remitInfo.beneficiaryBankName)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Bank Address" : "Beneficiary's Bank Address")
                .appendCell(remitInfo.beneficiaryBankAddress)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Bank SWIFT" : "Beneficiary's Bank SWIFT")
                .appendCell(remitInfo.beneficiaryBankSwiftCode)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Bank Country" : "Beneficiary's Bank Country")
                .appendCell(remitInfo.beneficiaryBankCountry)
        );

        pdf.append(pdf.table(new float[] {0.32f, 0.68f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender's Intermediary Bank Information" : "Beneficiary's Intermediary Bank Information", 8f, 2)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Intermediary Bank Name" : "Beneficiary's Intermediary Bank Name")
                .appendCell(remitInfo.isIntermediaryRequired ? remitInfo.intermediaryBankName : "")
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Intermediary Bank Address" : "Beneficiary's Intermediary Bank Address")
                .appendCell(remitInfo.isIntermediaryRequired ? remitInfo.intermediaryBankAddress : "")
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Intermediary Bank SWIFT" : "Beneficiary's Intermediary Bank SWIFT")
                .appendCell(remitInfo.isIntermediaryRequired ? remitInfo.intermediaryBankSwiftCode : "")
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Intermediary Bank Country" : "Beneficiary's Intermediary Bank Country")
                .appendCell(remitInfo.isIntermediaryRequired ?remitInfo.intermediaryBankCountry : "")
        );

        return pdf.toByteArray();
    }

    public ResponseEntity<ByteArrayResource> buildFiatReceipt(FiatTransaction txn, RemitInfo remitInfo, String clientName, String clientContact) {
        PdfProcessor pdf = PdfProcessor.create();
        this.pdfHeader(pdf, txn, txn.clientId, clientName, clientContact);
        Table itemTable = pdf.table(new float[] {0.08f, 0.17f, 0.16f, 0.13f, 0.46f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Item", 8f)
                .appendBlueCell(txn.type == FiatTransactionType.DEPOSIT ? "Deposit Amount" : "Withdrawal Amount", 8f)
                .appendBlueCell(txn.type == FiatTransactionType.DEPOSIT ? "Deposit Fees" : "Withdrawal Fees", 8f)
                .appendBlueCell("Net Amount", 8f)
                .appendBlueCell("Transaction hash", 8f);
        itemTable
                .appendCell(txn.currency)
                .appendCell(txn.amount)
                .appendCell(txn.transactionFee)
                .appendCell(txn.transactionFee == null ? txn.amount : txn.amount.subtract(txn.transactionFee))
                .appendCell(txn.referenceNo);
        pdf.append(itemTable);

        pdf.append(pdf.table(new float[] {0.32f, 0.68f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender's Bank Information" : "Beneficiary's Bank Information", 8f, 2)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Name" : "Beneficiary's Name")
                .appendCell(clientName)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Address" : "Beneficiary's Address")
                .appendCell(remitInfo.beneficiaryAddress)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Account" : "Beneficiary's Account")
                .appendCell(remitInfo.beneficiaryAccount)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Bank Name" : "Beneficiary's Bank Name")
                .appendCell(remitInfo.beneficiaryBankName)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Bank Address" : "Beneficiary's Bank Address")
                .appendCell(remitInfo.beneficiaryBankAddress)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Bank SWIFT" : "Beneficiary's Bank SWIFT")
                .appendCell(remitInfo.beneficiaryBankSwiftCode)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Bank Country" : "Beneficiary's Bank Country")
                .appendCell(remitInfo.beneficiaryBankCountry)
        );

        pdf.append(pdf.table(new float[] {0.32f, 0.68f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender's Intermediary Bank Information" : "Beneficiary's Intermediary Bank Information", 8f, 2)
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Intermediary Bank Name" : "Beneficiary's Intermediary Bank Name")
                .appendCell(remitInfo.isIntermediaryRequired ? remitInfo.intermediaryBankName : "")
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Intermediary Bank Address" : "Beneficiary's Intermediary Bank Address")
                .appendCell(remitInfo.isIntermediaryRequired ? remitInfo.intermediaryBankAddress : "")
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Intermediary Bank SWIFT" : "Beneficiary's Intermediary Bank SWIFT")
                .appendCell(remitInfo.isIntermediaryRequired ? remitInfo.intermediaryBankSwiftCode : "")
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Sender’s Intermediary Bank Country" : "Beneficiary's Intermediary Bank Country")
                .appendCell(remitInfo.isIntermediaryRequired ?remitInfo.intermediaryBankCountry : "")
        );


        return pdf.toResponseEntity(txn.type == FiatTransactionType.DEPOSIT ? "Fiat-deposit-" + txn.id : "Fiat-withdrawal-" + txn.id);
    }

    public byte[] toOtcReceipt(Otc otc, String clientName, String clientContact) {
        PdfProcessor pdf = PdfProcessor.create();
        this.pdfHeader(pdf, otc, otc.clientId, clientName, clientContact);

        Table itemTable = pdf.table(new float[] {0.08f, 0.17f, 0.16f, 0.18f, 0.18f, 0.18f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Type", 8f)
                .appendBlueCell("Crypto Currency", 8f)
                .appendBlueCell("Crypto Amount", 8f)
                .appendBlueCell("Exchange Rate", 8f)
                .appendBlueCell("Fiat Currency", 8f)
                .appendBlueCell("Fiat Amount", 8f);
        itemTable
                .appendCell(otc.type)
                .appendCell(otc.cryptoCurrency)
                .appendCell(otc.cryptoAmount)
                .appendCell(otc.rate)
                .appendCell(otc.fiatCurrency)
                .appendCell(otc.fiatAmount);
        pdf.append(itemTable);
        return pdf.toByteArray();
    }

    public ResponseEntity<ByteArrayResource> buildOtcReceipt(Otc otc, String clientName, String clientContact) {
        PdfProcessor pdf = PdfProcessor.create();
        this.pdfHeader(pdf, otc, otc.clientId, clientName, clientContact);

        Table itemTable = pdf.table(new float[] {0.08f, 0.17f, 0.16f, 0.18f, 0.18f, 0.18f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Type", 8f)
                .appendBlueCell("Crypto Currency", 8f)
                .appendBlueCell("Crypto Amount", 8f)
                .appendBlueCell("Exchange Rate", 8f)
                .appendBlueCell("Fiat Currency", 8f)
                .appendBlueCell("Fiat Amount", 8f);
        itemTable
                .appendCell(otc.type)
                .appendCell(otc.cryptoCurrency)
                .appendCell(otc.cryptoAmount)
                .appendCell(otc.rate)
                .appendCell(otc.fiatCurrency)
                .appendCell(otc.fiatAmount);
        pdf.append(itemTable);

        return pdf.toResponseEntity("OTC-" + otc.id);
    }


    public ResponseEntity<ByteArrayResource> payableReceipt(Payable payable, KycWalletAddress walletAddress, CryptoTransaction txn) {
        PdfProcessor pdf = PdfProcessor.create();
        this.pdfHeader(pdf, payable, walletAddress.ownerId, txn.requestTimestamp);

        Table itemTable = pdf.table(new float[] {0.08f, 0.17f, 0.16f, 0.13f, 0.46f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Item", 8f)
                .appendBlueCell("Withdrawal Amount", 8f)
                .appendBlueCell("Withdrawal Fees", 8f)
                .appendBlueCell("Net Amount", 8f)
                .appendBlueCell("Transaction ID/Hash", 8f);
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
                .appendBlueCell("Beneficiary’s Crypto Wallet Information", 8f, 2)
                .appendCell("Beneficiary’s Name")
                .appendCell(payable.beneficiary)
                .appendCell("Beneficiary’s Crypto Wallet Address")
                .appendCell(walletAddress.address)
                .appendCell("Beneficiary’s Crypto Wallet Network")
                .appendCell(walletAddress.mainNet.desc)
        );

        return pdf.toResponseEntity("Crypto-withdrawal-" + payable.id);
    }

    /**
     * Fiat Transaction payable info
     * @param payable
     * @param remitInfo
     * @param txn
     * @return
     */
    public ResponseEntity<ByteArrayResource> payableReceipt(Payable payable, RemitInfo remitInfo, FiatTransaction txn) {
        PdfProcessor pdf = PdfProcessor.create();
        this.pdfHeader(pdf, payable, remitInfo.ownerId, txn.createdTime);

        Table itemTable = pdf.table(new float[] {0.13f, 0.19f, 0.17f, 0.13f, 0.21f, 0.17f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Item", 8f)
                .appendBlueCell("Withdrawal Amount", 8f)
                .appendBlueCell("Withdrawal Fees", 8f)
                .appendBlueCell("Net Amount", 8f)
                .appendBlueCell("Beneficiary Reference", 8f)
                .appendBlueCell("DTC Reference", 8f);
        itemTable
                .appendCell(txn.currency)
                .appendFiatAmountCell(txn.amount)
                .appendFiatAmountCell(txn.transactionFee)
                .appendFiatAmountCell(txn.transactionFee == null ? txn.amount : txn.amount.subtract(txn.transactionFee))
                .appendCell(txn.referenceNo)
                .appendCell(payable.referenceNo);
        pdf.append(itemTable);

        pdf.append(pdf.table(new float[] {0.32f, 0.68f})
                .marginTop(20)
                .fontSize(6)
                .appendBlueCell("Beneficiary’s Bank Information", 8f, 2)
                .appendCell("Beneficiary’s Name")
                .appendCell(remitInfo.beneficiaryName)
                .appendCell("Beneficiary’s Address")
                .appendCell(remitInfo.beneficiaryAddress)
                .appendCell("Beneficiary’s Account")
                .appendCell(remitInfo.beneficiaryAccount)
                .appendCell("Beneficiary’s Bank Name")
                .appendCell(remitInfo.beneficiaryBankName)
                .appendCell("Beneficiary’s Bank Address")
                .appendCell(remitInfo.beneficiaryBankAddress)
                .appendCell("Beneficiary’s Bank SWIFT")
                .appendCell(remitInfo.beneficiaryBankSwiftCode)
                .appendCell("Beneficiary’s Bank Country")
                .appendCell(remitInfo.beneficiaryBankCountry)
                .appendBlueCell("Beneficiary’s Intermediary Bank Information", 8f, 2)
                .appendCell("Beneficiary’s Intermediary Bank Name")
                .appendCell(remitInfo.intermediaryBankName)
                .appendCell("Beneficiary’s Intermediary Bank Address")
                .appendCell(remitInfo.intermediaryBankAddress)
                .appendCell("Beneficiary’s Intermediary Bank SWIFT")
                .appendCell(remitInfo.intermediaryBankSwiftCode)
                .appendCell("Beneficiary’s Intermediary Bank Country")
                .appendCell(remitInfo.intermediaryBankCountry)
        );
        return pdf.toResponseEntity("Fiat-withdrawal-" + payable.id);
    }

    private void pdfHeader(PdfProcessor pdf, CryptoTransaction txn, Long clientId, String clientName, String clientContact) {
        pdf.append(pdf.table(new float[] {0.15f, 0.87f})
                .fontSize(9)
                .appendBlueCell("Order ID")
                .appendCell(txn.id)
                .appendBlueCell("Order Type")
                .appendCell(txn.type == CryptoTransactionType.DEPOSIT ? "Crypto Deposit" : "Crypto Withdrawal")
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

    private void pdfHeader(PdfProcessor pdf, FiatTransaction txn, Long clientId, String clientName, String clientContact) {
        pdf.append(pdf.table(new float[] {0.18f, 0.87f})
                .fontSize(9)
                .appendBlueCell("Order ID")
                .appendCell(txn.id)
                .appendBlueCell("Order Type")
                .appendCell(txn.type == FiatTransactionType.DEPOSIT ? "Fiat Deposit" : "Fiat Withdrawal")
        ).append(pdf.table(new float[] {0.18f, 0.37f, 0.18f, 0.37f})
                .marginTop(7)
                .fontSize(8)
                .appendCell("Client ID", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientId)
                .appendCell("Order Date", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(txn.completedTime != null ? txn.completedTime.format(DateTime.FORMAT.READABLE_DATE) : "")
                .appendCell("Client Name", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientName)
                .appendCell("Email", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientContact)
        );
    }

    private void pdfHeader(PdfProcessor pdf, Otc otc, Long clientId, String clientName, String clientContact) {
        pdf.append(pdf.table(new float[] {0.18f, 0.87f})
                .fontSize(9)
                .appendBlueCell("Order ID")
                .appendCell(otc.id)
                .appendBlueCell("Order Type")
                .appendCell("Over-the-counter(OTC)")
        ).append(pdf.table(new float[] {0.18f, 0.37f, 0.18f, 0.37f})
                .marginTop(7)
                .fontSize(8)
                .appendCell("Client ID", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientId)
                .appendCell("Order Date", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(otc.completedTime.format(DateTime.FORMAT.READABLE_DATE))
                .appendCell("Client Name", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientName)
                .appendCell("Approver", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(otc.operator)
                .appendCell("Email", PdfProcessor.COLOR_DARK_BLUE)
                .appendCell(clientContact)
        );
    }

    private void pdfHeader(PdfProcessor pdf, Payable payable, Long clientId, LocalDateTime orderDate) {
        pdf.append(pdf.table(new float[] {0.18f, 0.87f})
                .fontSize(9)
                .appendBlueCell("Order ID")
                .appendCell(payable.id)
                .appendBlueCell("Order Type")
                .appendCell("Fiat Withdrawal")
        ).append(pdf.table(new float[] {0.18f, 0.37f, 0.18f, 0.37f})
                        .marginTop(7)
                        .fontSize(8)
                        .appendCell("Client ID", PdfProcessor.COLOR_DARK_BLUE)
                        .appendCell(clientId)
                        .appendCell("Order Date", PdfProcessor.COLOR_DARK_BLUE)
                        .appendCell(orderDate.format(DateTime.FORMAT.READABLE_DATE))
                        .appendCell("Client Name", PdfProcessor.COLOR_DARK_BLUE)
                        .appendCell(payable.beneficiary)
                        .appendCell("", PdfProcessor.COLOR_DARK_BLUE)
                        .appendCell("")
        );
    }

}