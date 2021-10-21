## RELEASE NOTES [dtc-settlement-engine] ##

[0.49.0] -

* Remove FTX

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

