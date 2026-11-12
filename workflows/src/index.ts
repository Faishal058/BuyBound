import 'dotenv/config';
import { parallelStoreScanTask } from './tasks/storeScannerTask.js';
import { aiForensicsTask } from './tasks/aiForensicsTask.js';
import { sellerReputationTask } from './tasks/sellerReputationTask.js';
import { settlementTask } from './tasks/settlementTask.js';
import { overnightDropWorkflow } from './workflows/overnightDropWorkflow.js';

export {
  parallelStoreScanTask,
  aiForensicsTask,
  sellerReputationTask,
  settlementTask,
  overnightDropWorkflow
};

console.log('----------------------------------------------------');
console.log('  BuyBound Render Workflows Worker Initialized');
console.log('  Registered Tasks:');
console.log('   - parallelStoreScanTask (distributed store scan)');
console.log('   - aiForensicsTask (AI prompt injection & SKU firewall)');
console.log('   - sellerReputationTask (merchant reputation & telemetry)');
console.log('   - settlementTask (PayPal checkout & wallet settlement)');
console.log('  Registered Workflows:');
console.log('   - overnightDropWorkflow (orchestrates distributed hunts)');
console.log('----------------------------------------------------');

// When deployed on Render Workflows, the task server starts automatically
// when RENDER_SDK_SOCKET_PATH is set.
if (process.env.RENDER_SDK_SOCKET_PATH) {
  console.log(`[Workflows] Connecting to Render Workflow socket: ${process.env.RENDER_SDK_SOCKET_PATH}`);
} else {
  console.log('[Workflows] Running in standalone / local development mode.');
  console.log('Use "npm run hunt" or "npm run drop" to trigger workflows locally.');
}
