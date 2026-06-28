const fs = require('fs');
fs.copyFileSync('FloatingReaderService.kt.bak', 'app/src/main/java/com/example/service/FloatingReaderService.kt');
console.log("Restored");
