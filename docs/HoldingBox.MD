# The Holding Boxes
The holding boxes are a collection of boxes that hold the current value obtained from
mining rewards. These boxes verify that a valid metadata box and command box are being used
in the distribution transaction. The holding boxes also verify that there is an output box for each
miner in the Smart Pool, and that these output boxes follow the share consensus provided by command
box.

The holding box is the address that mining rewards are sent to. Once sent to this address, the ERG is held
in the holding address until a valid consensus tx occurs. Because the holding contract is separate
from the consensus contract, we may use any method of distribution by changing the holding script.
The default holding script pays proportionally to the share values held in the holding box. This allows
for PROP and PPLNS payment methods by supplying the correct share values to the command box during a
distribution transaction.

The holding box will automatically unlock if it has not been spent after 20000 blocks.