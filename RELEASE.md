## RELEASE NOTES [dtc-settlement-engine] ##

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

