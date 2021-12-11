# The Holding Boxes
The holding boxes are a collection of boxes that hold the current value obtained from
mining rewards. These boxes verify that a valid metadata box and command box are being used
in the distribution transaction. The holding boxes also verify that there is an output box for each
miner in the Smart Pool, and that these output boxes follow the share consensus provided by the command
box.

## Details
The holding contract is made using the proposition bytes of the metadata script along with the SmartPool Id.
The SmartPool Id is the id of the genesis box, it is verified by checking for the SmartPool NFT in the
metadata box.

The holding box is the address that mining rewards are sent to. Once sent to this address, the ERG is held
in the holding address until a valid distribution tx occurs. Because the holding contract is separate
from the consensus contract, we may use any method of distribution by changing the holding script.
The default holding script pays proportionally to the share values held in the holding box. This allows
for PROP and PPLNS payment methods by supplying the correct share values to the command box during a
distribution transaction.

## Minimum Payouts
The holding box also handles minimum payouts by monitoring the command box's share consensus to analyze
if balances are held correctly. To ensure the proper value is kept, all holding boxes must be used in any
given distribution transaction.

## Custom Holding Boxes
We may make custom holding boxes that change how payments are sent. Custom holding boxes can be used
to add additional requirements for payments, along with additional record keeping for certain statistics.

### Example
Let's imagine a smart pool which allows users to convert some percentage of their mining rewards into a
a governance token for a new dApp. We can use the holding box to require an additional element in each
share consensus that allows us to identify what percentage of the user's mining rewards will go to
to the tokens. The holding box can then use that percentage to verify that each output box has the correct
ratio of tokens to mining rewards based on some conversion rate that is stored as a constant in the contract.

