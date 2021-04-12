## RELEASE NOTES [dtc-settlement-engine] ##

[0.21.0] - 12 Apr 2021

* Added EnableSettlementEngine annotation

[0.20.0] - 08 Apr 2021

* Remove useless redis config
* Create Unirest instance for Silvergate

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

