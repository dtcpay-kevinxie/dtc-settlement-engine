## RELEASE NOTES [dtc-settlement-engine] ##

[0.100.0] - 11 Nov 2022

* payoutReconcile.receivedCurrency missing

[0.99.0] - 11 Nov 2022

* CryptoTxnChainService remove TRON special logics

[0.98.0] - 10 Nov 2022

* Sweep to external crypto address
* VipScheme date range optimize

[0.97.0] - 01 Nov 2022

* Upgrade dtc_parent version to '0.58.0'

[0.96.0] - 01 Nov 2022

* Add OtcBonusProcess logic

[0.95.0] - 24 Oct 2022

* MatchMoveInitConfig optimization
* Update parent & data-* & addon-*
* CryptoTxnChainService sweepChain
* Complete Receivable process logic and Settlement logic
* Add Settlement Report
* Optimize Lombok annotations usages
* Set Java DNS TTL to 5 in the Dockerfile
* Stop using BeanUtils.copyProperties
* Fix send-settlement-report
* Fix Crypto rate calculation

[0.94.0] - 29 Sep 2022

* Hotfix integration url normalize
* Update parent version

[0.93.0] - 27 Sep 2022

* Update parent
* Add deps addon-integration & addon-data-processor
* Find/check wallet address logic update
* Init MatchMove webhooks
* Remove FiatTransaction from Domestic and Cross-border report

[0.92.0] - 21 Sep 2022

* Add BalanceChangeHistoryReport to PSN04 1A
* Standardize rateMaps usage

[0.91.0] - 20 Sep 2022

* Update data-finance
* Change self-custodial wallet crypto payment settlement logic

[0.90.0] - 14 Sep 2022

* Update data-core
* Add client status filters for MonitoringMatrix report
* Add daily-balance-check scheduler
* Change PSN04 report cross-border/domestic calculation logic

[0.89.0] - 05 Sep 2022

* Update parent

[0.88.0] - 24 Aug 2022

* Update parent & data-*
* Update to Java 17 with features migration
* log4j2-spring-local.xml
* Add CryptoNotification
* Remove Satoshi Test scheduled process
* Correct the EnableScheduling importing

[0.87.0] - 18 Aug 2022

* Update data-finance version

[0.86.0] - 15 Aug 2022

* Change Binance settlement email recipient
* Add licensing date for MAS report generating logic

[0.85.0] - 11 Aug 2022

* Update parent
* Align changes with the crypto-engine APIs
* Add notification_url TODO to cryptoTransaction
* Excluding stable coin when getting rate from FTX API

[0.84.0] - 02 Aug 2022

* Change crypto rate getting method

[0.83.0] - 01 Aug 2022

* MAS Report templates formatting
* Add logs for report generating

[0.82.0] - 29 Jul 2022

* Update data-wallet
* Complete MAS Report processor and service

[0.81.0] - 27 Jul 2022

* Update parent & date-* & poi
* Dockerfile install font libs
* Add MAS Reporting Module

[0.80.0] - 22 Jul 2022

* Update data-wallet

[0.79.0] - 21 Jul 2022

* Add TRX anti-dust amount

[0.78.0] - 20 Jul 2022

* Update parent
* Anti-dust feature support
* log4j2-spring-local.xml
* Correct the EnableScheduling importing

[0.77.0] - 13 Jul 2022

* Update parent, data-wallet, data-finance, data-core
* SchedulerTasks execution optimize
* WalletBalanceHistory related

[0.76.0] - 01 Jul 2022

* /balance/scheduled report

[0.75.0] - 28 Jun 2022

* Update dtc-parent and data-finance
* Save InternalTransfer writeOffDate 

[0.74.0] - 27 Jun 2022

* Update GP transaction SettlementStatus

[0.73.0] - 20 Jun 2022

* Fix otc commission calculation

[0.72.0] - 17 Jun 2022

* Move OTC Settlement crypto received logic to txnHash exists case
* Fix otc commission calculation

[0.71.0] - 16 Jun 2022

* Hotfix commission calculation bug when fiatConverting applied

[0.70.0] - 16 Jun 2022

* Add scheduled crypto exchange rate from FTX
* Update data-finance version
* Standardize OTC Commission logic
* Add daily balance record logic
* Complete Self-Custodial Wallet payment settlement process
* Add USDC support
* Code reformat in SilvergateApiService
* Update USDC in CryptoTransactionProcessService

[0.69.0] - 18 May 2022

* Add OTC commission scheduled API
* Remove profit check when generate OTC commission;
* Update dtc-parent, data-core, data-finance versions
* Add PayoutReconcile generating from scheduled settlement process
* Update parent

[0.68.0] - 26 Apr 2022

* Add BalanceHistory process
* Update dtc-parent, data-core and data-risk version
* Add SELF_CUSTODIAL logic when receive notification from crypto-engine
* Add OTC Commission scheduled, remove profit check logic when generate OTC commission
* Change settlement scheduler API to GET

[0.67.0] - 04 Apr 2022

* Update parent & data-*
* Crypto Payment callback
* MatchMove Payment callback

[0.66.0] - 17 Mar 2022

* Suspend ExchangeRate API Calls
* Update parent & data-*
* Use enum Module

[0.65.0] - 01 Mar 2022

* Send notification with PDF file attachment
* Update dtc-parent

[0.64.0] - 22 Feb 2022

* Upgrade data-finance

[0.63.0] - 22 Feb 2022

* Upgrade parent & data-* version
* Add SSE Trigger for wallet account updated
* Add OtcCommission logic 
* Remove Currency APIs instead of using Currency Enum

[0.62.0] - 03 Feb 2022

* Change recipient of internal address alerts to Ops instead of Compliance

[0.61.0] - 26 Jan 2022

* Hotfix chainalysis v2 API GET method
* Add SSE Trigger for wallet account updated
* Upgrade parent & data-*

[0.60.0] - 18 Jan 2022

* Hotfix chainalysis v2 API GET method

[0.59.0] - 13 Jan 2022

* Update data-* and parent dep version
* Fix chainalysis v2 url bug
* Disable sweep after deposit

[0.58.0] - 07 Jan 2022

* Rollback data-core dep version
* Fix sweep to OPS address reject case bug 
* Migrate to Chainalysis v2 API 

[0.57.0] - 20 Dec 2021

* Upgrade log4j, https://logging.apache.org/log4j/2.x/security.html

[0.56.0] - 16 Dec 2021

* Add TRC-20 logic reference
* Remove OpsEmail notification
* Crypto withdrawal completed email displays as amount - fee

[0.55.0] - 12 Dec 2021

* HOTFIX: Upgrade log4j, https://www.lunasec.io/docs/blog/log4j-zero-day/

[0.54.0] - 11 Dec 2021

* HOTFIX: Crypto handleSuccessTxn false alert

[0.53.0] - 10 Dec 2021

* Hotfix: Crypto PROCESSING txn handling

[0.52.0] - 09 Dec 2021

* Hotfix: Handle sweep to DTC_FINANCE case

[0.51.0] - 29 Nov 2021

* Update parent, migrate crypto-engine related object usages
* Fix Silvergate API payment_status
* Change auto-sweep address logic

[0.50.0] - 27 Oct 2021

* Fix multiple PENDING satoshi logic bug
* Add receivable after satoshi

[0.49.0] - 26 Oct 2021

* Remove FTX
* Update fill gas InternalTransfer status

[0.48.0] - 19 Oct 2021

HOTFIX: Update data-finance

[0.47.0] - 18 Oct 2021

* Send both recipient address and index for internal crypto transfer

[0.46.0] - 13 Oct 2021

* Hotfix save InternalTransfer only when sweep accepted by blockchain.

[0.45.0] - 11 Oct 2021

* Change sweep request parameters to index instead of address
* Save sweep and gas fill-up transaction to internal_transfer
* Update parent & data-wallet
* FTX Portal API integration

[0.44.0] - 30 Sep 2021

* Hotfix CrpytoTransaction state when notify

[0.43.0] - 28 Sep 2021

* Hotfix transfer addressIndex and accountIndex
* Silvergate notification logic

[0.42.0] - 27 Sep 2021

* Handle decimal when get amount from blockchain
* Update dtc-parent version and *-data dep version
* Fix Silvergate notify balance checking bug
* Register txn to Chainalysis after notified by blockchain network

[0.41.0] - 20 Sep 2021

* Complete crypto notify logic
* Fix Silvergate notify logic
* Remove etherscan api integration

[0.40.0] - 15 Sep 2021

* Fix deposit crypto_transaction state not updated bug
* Fix multiple email recipients bug 
* Update withdrawalCompleted Notification
* Add Receivable when after crypto deposit completed

[0.39.0] - 14 Sep 2021

* Update data-wallet version

[0.38.0] - 13 Sep 2021

* Enable EnableDataWalletAws

[0.37.0] - 10 Sep 2021

* Update dtc-parent dep version
* Update cryptoTransactionProcessService

[0.36.0] - 09 Sep 2021

* Update *-data dep version
* Complete notify withdrawal logic
* Apply txnFee and gasFee logic

[0.35.0] - 07 Sep 2021

* Optimize auto-sweep logic, fix log and notification bug
* Handle blockchain REJECTED case
* Fix gas refill notify bug
* Remove Payable and Receivable write-off logic

[0.34.0] - 31 Aug 2021

* Fix auto-sweep bug

[0.33.0] - 25 Aug 2021

* Update data-* version
* Merge IndividualStatus and NonIndividualStatus to ClientStatus

[0.32.0] - 23 Aug 2021

* Fix satoshi test logic bug in notify API

[0.31.0] - 16 Aug 2021

* Update dtc-parent and data-* version
* Remove unused classes
* Standardize Silvergate integration service

[0.30.0] - 10 Jul 2021

* Add the scheduler for auto-sweep logic 
* Add the sweep logic when deposit amount exceeds sweepThreshold
* Update dtc-parent dep version
* Rename dtc_config table; Rename dtc_otc columns

[0.29.0] - 07 Jul 2021

* Add cryptoTransaction withdraw logic reference
* Add a scheduler for Satoshi_Test status check
* Remove Withdrawal-complete & withdrawal-cancel & withdrawal-request process
* Add cryptoTransaction notification logic

[0.28.0] - 28 May 2021

* Update data-* version
* Remove ClientAccountProcessService

[0.27.0] - 27 May 2021

* Fix etherscan verify ETH transaction bug

[0.26.0] - 24 May 2021

* Change blockchain scheduler logic

[0.25.0] - 18 May 2021

* Silvergate TransferSEN API & WireDetail & WireSummary API reference
* Standardize Silvergate Notification

[0.25.0] - 07 May 2021

* Add Silvergate log
* Standardize Silvergate notification format

[0.24.0] - 28 Apr 2021

* Added Silvergate Webhook SMS config 

[0.24.0] - 29 Apr 2021

* Fix AccountHistory null bug
* Add logs

[0.23.0] - 23 Apr 2021

* Change Transaction to PaymentTransaction
* Update dtc-parent & data-*

[0.22.0] - 23 Apr 2021

* Fix the docker base image version to 11.0.10-jre-slim-buster
* Fix kycWalletAddress updateById

[0.21.0] - 20 Apr 2021

* Create Unirest instance for Silvergate
* Update parent & data-*

[0.20.0] - 08 Apr 2021

* Remove useless redis config

[0.19.0] - 01 Apr 2021

* Change to multiple Silvergate accounts logic
* Apply Silvergate API secondary subscription key logic

[0.18.0] - 12 Mar 2021

* Add Silvergate WebHoot when init Settlement Engine
* Add Silvergate WebHootUrl

[0.17.0] - 10 Mar 2021

* Migrate Merchant to NonIndividual
* Add Silvergate Integration

[0.16.0] - 22 Feb 2021

* Add Silvergate reference APIs 
* Added some silvergate modules 
* Updated getAccessToken logic using Redis 

[0.15.0] - 04 Feb 2021

* Update parent
* Add receivable-info and payable-info to OTC_AGREE notification
* Upgrade data-risk

[0.14.0] - 28 Jan 2021

* Update parent
* NotificationBuilder
* Change Receivable write-off amount validation.

[0.13.0] - 25 Jan 2021

* Register txn to Chainalysis

[0.13.0] - 21 Jan 2021

* Register txn to Chainalysis

[0.12.0] - 21 Jan 2021

* Hotfix receivedAmount bug

[0.11.0] - 20 Jan 2021

* Hotfix Transactional bug

[0.10.0] - 20 Jan 2021

* Hotfix Transactional bug
* Upgrade data-finance

[0.9.0] - 19 Jan 2021

* Hotfix Receivable and Payable generating bug

[0.8.0] - 19 Jan 2021

* Update parent & data-core

[0.7.0] - 19 Jan 2021

* Hotfix Receivable and Payable generating bug

[0.6.0] - 15 Jan 2021

* Add auto-scan and write-off feature for ERC-20 transactions
* Change OTC account validation logic
* Upgrade dtc-parent

[0.5.0] - 04 Jan 2021

* Add cancel OTC Receivable and Payable logic

[0.4.0] - 22 Dec 2020

* Change PayableDate logic
* Change OTC email notification format
* Add Etherscan API to validate txnHash

[0.3.0] - 17 Dec 2020

* Fix OTC Receivable write-off bug
* Standardize notification email id
* Fix Payable not updated bug
* PayableDate is updated after Receivable is written-off
* Update data-core

[0.2.0] - 10 Nov 2020

* Update application.yml
* Change jetty to undertow
* Update Aleta settlement day calculation
* Fix reconcile transactionId bug
* Fix invoice amount bug
* Fix transaction state bug
* Fix Settlement finalAmount calculation
* ApiHeader
* Settlement Batch command
* Update parent data-finance data-risk
* Make generatePayableAndReceivable Transactional
* Email Notification
* Fix PostMapping bug
* Update data-risk

[0.1.0] - 09 Nov 2020

* Init repo

