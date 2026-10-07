# System Monitoring — API สำหรับฝั่ง UI

ส่วนนี้ดึงข้อมูล System / Process แล้วส่งออกเป็น snapshot แบบ immutable — ฝั่ง UI แค่ subscribe แล้วเอาไปแสดง ไม่ต้องยุ่งกับ OSHI / JNA / thread เอง

## เริ่มใช้งาน

```java
SystemMonitorService monitor = new SystemMonitorService();

// เรียกบน JavaFX Application Thread ทุก ~1 วินาที — แก้ UI ได้เลย
monitor.addListener(snapshot -> cpuLabel.setText(FormatUtils.percent(snapshot.cpu().usagePercent())));

// หรือ bind กับ property (เป็น null จนกว่าจะได้ sample แรก ~1 วิหลัง start)
monitor.snapshotProperty().addListener((obs, old, snapshot) -> { ... });

monitor.start();   // ใน Application.start()
monitor.stop();    // ใน Application.stop() — หยุด background thread
```

ตอนนี้ `App.java` ต่อไว้แล้ว: field `systemMonitor` + placeholder `systemInfoLabel` (ข้อความธรรมดา, มี `// TODO(UI)`) — ฝั่ง UI เอา listener ไปผูกกับ Card แทน label นี้ได้เลย

## `SystemSnapshot`

ค่าที่ดึงไม่ได้จะเป็น `null` (list จะเป็น list ว่าง) → ให้ UI แสดง `N/A` (`FormatUtils` คืน `"N/A"` ให้อัตโนมัติเมื่อส่ง null/ค่าติดลบเข้าไป)

| Field | Type | หน่วย / ความหมาย |
|---|---|---|
| `timestamp` | `Instant` | เวลาที่ sample |
| `cpu.usagePercent` | `double` | CPU ทั้งเครื่อง 0–100 |
| `cpu.model` | `String` | เช่น `Intel(R) Core(TM) i9-10900K CPU @ 3.70GHz` |
| `cpu.physicalCores` / `logicalCores` | `int` | Task Manager "Cores" / "Logical processors" |
| `cpu.maxFreqHz` / `currentFreqHz` | `Long` (nullable) | Hz — ดูหมายเหตุ CPU Frequency |
| `memory.totalBytes` / `usedBytes` / `availableBytes` | `long` | bytes, used = total − available |
| `memory.usedPercent` | `double` | 0–100 |
| `memory.virtualUsedBytes` / `virtualTotalBytes` | `long` | Task Manager "Committed" (ซ้าย / ขวา) |
| `disks[]` | `List<DiskInfo>` | ทุก drive ในเครื่อง (ไม่รวม network drive): `mount` (`C:\`), `name`, `totalBytes`, `usedBytes`, `freeBytes`, `usedPercent` |
| `diskIo.readBytesPerSec` / `writeBytesPerSec` | `double` (object nullable) | bytes/s รวมทุก physical disk |
| `gpus[]` | `List<GpuInfo>` | `name`, `usagePercent` (`Double`, nullable), `vramUsedBytes` (`Long`, nullable), `vramTotalBytes`, `supported` (true = อ่าน usage และ VRAM used ได้ทั้งคู่) |
| `network.downloadBytesPerSec` / `uploadBytesPerSec` | `double` | bytes/s |
| `network.totalThroughputBytesPerSec` | `double` | download + upload |
| `network.totalBytesReceived` / `totalBytesSent` | `long` | สะสมตั้งแต่ adapter เปิด (ปกติคือตั้งแต่เปิดเครื่อง) |
| `uptimeSeconds` | `Long` | วินาที |
| `processCount` / `runningProcessCount` | `Integer` | ดูหมายเหตุ Running Process |

## `FormatUtils`

| Method | ตัวอย่าง |
|---|---|
| `bytes(long)` | `47.9 GB` (หน่วย 1024 แบบ Task Manager) |
| `rate(double)` | `350.0 KB/s` |
| `percent(double)` | `12.8%` |
| `uptime(long)` | `2d 03:14:05` |
| `frequency(Long)` | `3.70 GHz` |
| `summary(SystemSnapshot)` | ข้อความหลายบรรทัด (ใช้กับ placeholder เท่านั้น) |

## Process table

- `ProcessInfo.getThreadCount()` — จำนวน thread, `ProcessInfo.UNKNOWN` (-1) ถ้าอ่านไม่ได้
- `ProcessInfo.getMemoryBytes()` — Working Set (bytes), -1 ถ้าอ่านไม่ได้
- column **Threads**, **Memory**, **CPU Usage** เก็บค่าเป็นตัวเลข → sort ถูกต้อง (กดหัว column ครั้งแรก = น้อย→มาก, ครั้งที่สอง = มาก→น้อย)
- `ProcessService` ดึงข้อมูล thread/memory จาก OSHI ไม่เกินทุก ~5 วินาที (ตาม process list refresh) แม้ CPU usage จะ refresh ทุก 1 วินาที

## หมายเหตุ / ข้อจำกัด (สาเหตุที่บางค่าอาจเป็น N/A หรือต่างจาก Task Manager)

- **CPU Frequency** — Windows รายงาน "current" ผ่าน API นี้เป็นค่า base clock (เช่น 3.70 GHz) ไม่ใช่ความเร็ว boost แบบ real-time ที่ Task Manager แสดงในช่อง "Speed" → ให้ถือว่า `maxFreqHz` = Base speed
- **Running Process** — นับ process ที่ state = RUNNING ตาม OSHI บน Windows จะไม่นับแค่ process ที่ถูก suspend (เช่น UWP app ที่อยู่เบื้องหลัง) จึงมักเท่ากับ `processCount`
- **Network** — Windows มี adapter ซ้ำหลายชั้น (WFP / QoS filter) ที่นับ byte เดียวกัน จึงนับเฉพาะ adapter จริงที่สถานะ UP (`isConnectorPresent`) เพื่อไม่ให้นับซ้ำ; traffic ของ VPN วิ่งผ่าน adapter จริงอยู่แล้ว
- **GPU Usage %** — คำนวณเองจาก performance counter `\GPU Engine(*)\Running Time` (ชื่อ counter ภาษาอังกฤษ ใช้ได้กับ Windows ทุกภาษา) แล้วเอา engine ที่ใช้งานสูงสุดของแต่ละ GPU แบบเดียวกับ Task Manager — **ไม่ใช้** `GpuStats.getGpuUtilization()` ของ OSHI 6.12.0 เพราะบั๊ก (เทียบ LUID ตัวเล็ก/ใหญ่ไม่ตรง → คืน 100% ตลอด)
- **VRAM Used** — `\GPU Adapter Memory(*)\Dedicated Usage` (= Task Manager "Dedicated GPU memory"); ถ้าอ่านไม่ได้จะ fallback ไปใช้ค่าจาก driver ผ่าน OSHI
- **GPU บน macOS / VM / ไม่มี driver** — `usagePercent` / `vramUsedBytes` เป็น null (`supported = false`) โปรแกรมไม่ crash
- **Disk Read/Write** — enumerate physical disk ครั้งเดียวตอน start; USB drive ที่เสียบทีหลังจะไม่ถูกนับใน read/write rate (แต่ยังแสดงพื้นที่ใน `disks[]`)
- **Process Memory** — เป็น Working Set; column "Memory" ปกติของ Task Manager > Details คือ *private* working set ซึ่งจะน้อยกว่า → ให้เทียบกับ column "Working set (memory)" แทน
- ใช้ OSHI 6.12.0 (`pom.xml`) และ JNA 5.18.1 ให้ตรงกับที่ OSHI ต้องการ; GPU ใช้ class ใน package `oshi.driver.windows.perfmon` / `oshi.util.gpu` ถ้าอัปเกรด OSHI ให้เช็คว่า API ยังอยู่

## Threading

- sample ทั้งหมดทำบน daemon thread ชื่อ `system-monitor` ทุก 1 วินาที (fixed delay); rate ทุกตัวคำนวณจาก delta / เวลาจริง (`System.nanoTime()`)
- listener / property ถูกเรียกผ่าน `Platform.runLater` เสมอ
- ถ้า metric ตัวไหนพัง จะ log ครั้งเดียวแล้วคืน null เฉพาะตัวนั้น ตัวอื่นยังทำงานต่อ

## Manual test บน Windows (เทียบ Task Manager)

| ID | ทดสอบ | เทียบกับ |
|---|---|---|
| S1 | CPU % | Performance > CPU "Utilization" |
| S2 / S6 | RAM used / available / committed | Performance > Memory "In use" / "Available" / "Committed" |
| S3.1 | พื้นที่ทุก drive | This PC |
| S3.2 | copy ไฟล์ใหญ่ | Performance > Disk "Read/Write speed" (รวมทุก disk) |
| S4 | เปิดวิดีโอ / เกม | Performance > GPU (graph บนสุด) และ "Dedicated GPU memory" |
| S5 | CPU model / cores | Performance > CPU มุมขวาบน, "Cores", "Logical processors", "Base speed" |
| S7 | ดาวน์โหลดไฟล์ | Performance > Ethernet / Wi-Fi (Task Manager แสดงเป็น bit/s — หาร 8) |
| S8 | Uptime | Performance > CPU "Up time" |
| S9 | จำนวน process | Performance > CPU "Processes" |
| P1 / P2 | Threads / Memory ของ `chrome.exe`, `java.exe` | Details (เพิ่ม column "Threads" และ "Working set (memory)") |
| P3 | sort CPU Usage มาก→น้อย | process ที่กิน CPU สูงสุดต้องอยู่บนสุด |
