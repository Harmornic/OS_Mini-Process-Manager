# Task Brief: System / Process Monitoring — Mini Process Manager

> เอกสารนี้เป็น brief สำหรับ Claude Code ให้พัฒนา **ส่วน System Monitoring** ของโปรเจกต์ Mini Process Manager ต่อ
> **ขอบเขตของงานนี้คือ System Monitoring เท่านั้น — ส่วน UI (ความสวยงาม, Card, Layout, Theme) มีเพื่อนในทีมทำแยก ห้ามรื้อหรือ redesign UI**

---

## 0. ก่อนเริ่มเขียนโค้ด (สำคัญ)

1. อ่านโครงสร้างโปรเจกต์ทั้งหมดก่อน โดยเฉพาะ `pom.xml`, `App.java`, `ProcessService.java`, `WindowsProcessMetrics.java`, `ProcessInfo.java`
2. สรุปให้ฉันดูว่าเข้าใจโครงสร้างปัจจุบันอย่างไร และ **เสนอแผน (plan) + รายชื่อไฟล์ที่จะสร้าง/แก้** ก่อนลงมือ
3. ทำงานบน branch แยก เช่น `feature/system-monitoring`
4. ทำทีละ step (ดูหัวข้อ 6) และให้ `./mvnw clean package` ผ่านทุกครั้งหลังจบแต่ละ step

---

## 1. Project Context

| รายการ | ค่า |
|---|---|
| Language | Java — JDK 21 |
| Build | Maven 3.9.x (มี Maven Wrapper `mvnw` / `mvnw.cmd`) |
| GUI | JavaFX 21.0.6 (เขียนด้วย Java ไม่ใช้ FXML) |
| Native access | JNA (ใช้อยู่แล้วใน `WindowsProcessMetrics.java`) |
| Dev OS | macOS |
| **Target OS** | **Windows** (ฟีเจอร์ hardware ต้องเน้นให้ทำงานถูกบน Windows) |
| Base package | `com.example.processmanager` |

### ไฟล์ที่มีอยู่แล้ว

- `App.java` — JavaFX UI ทั้งหมด (~1,300 บรรทัด ใหญ่มาก): Table, Search, Filter, Sort, Auto Refresh (5 วิ), Process Details, Process Tree, Process History
- `service/ProcessService.java` — ดึงข้อมูล Process ผ่าน `ProcessHandle` (PID, Parent PID, Name, Command/Path, Status, Start Time, CPU Time)
- `WindowsProcessMetrics.java` — ดึง CPU Time บน Windows ผ่าน JNA
- `model/ProcessInfo.java` — model ของ Process (PID, Parent PID, Name, Command/Path, Status, Start Time, CPU Time, CPU Usage)
- `ProcessTreeNode.java`, `ProcessEvent.java` — model ของ Tree และ History

ฟีเจอร์ที่ทำเสร็จแล้ว: Search, Sort, Filter by Status, Refresh, Auto Refresh, CPU Usage Monitoring (วัดทุก 1 วิ), Process Details, Process Tree, Tree Descendant Count, Process History (CREATED/TERMINATED), Kill Process, Start Time

---

## 2. ขอบเขตงาน (Scope)

### ✅ ต้องทำ — System Information

| ฟีเจอร์ | รายละเอียด |
|---|---|
| CPU Usage (%) ทั้งเครื่อง | การใช้ CPU โดยรวม |
| CPU Information | CPU Model, จำนวน Physical Core / Logical Processor, CPU Frequency (ถ้าดึงได้) |
| RAM / Memory Usage + System Memory Information | Total / Used / Available (+ % used) — สองหัวข้อนี้ใน brief เดิมคือข้อมูลชุดเดียวกัน ใช้ `MemoryInfo` ตัวเดียว |
| Disk Usage | พื้นที่ Used / Free / Total ต่อ drive, และ Disk Read / Write (bytes/sec) ถ้าทำได้ |
| GPU Usage | GPU Name, GPU Usage (%), VRAM Used / Total |
| Network Usage | Download / Upload (bytes/sec) และ throughput รวม |
| System Uptime | ระยะเวลาที่เครื่องเปิดใช้งาน |
| Number of Processes | จำนวน Process ทั้งหมด และจำนวน Running Process |

### ✅ ต้องทำ — Process Information เพิ่มเติม

| ฟีเจอร์ | รายละเอียด |
|---|---|
| Thread Count | จำนวน Thread ของแต่ละ Process |
| Memory Usage | RAM ที่แต่ละ Process ใช้ (Working Set / RSS) |
| CPU Usage sort | ตรวจว่า sort ตาม CPU Usage เป็นแบบ **ตัวเลข** (ไม่ใช่ string) และ sort มาก→น้อยได้ |

### 📋 Requirement ต้นฉบับจากทีม (verbatim — ต้องทำครบทุกข้อ)

```
System Information
* CPU Usage (%) ของทั้งเครื่อง
   * แสดงการใช้งาน CPU โดยรวมของเครื่อง
* RAM / Memory Usage
   * Total RAM
   * Used RAM
   * Available RAM
* Disk Usage
   * พื้นที่ Disk ที่ใช้ / เหลือ
   * ถ้าทำได้ เพิ่ม Disk Read / Write
* GPU Usage
   * GPU Usage (%)
   * VRAM Usage
* CPU Information
   * CPU Model
   * จำนวน Core / Logical Processor
   * CPU Frequency ถ้าสามารถดึงข้อมูลได้
* System Memory Information
   * Total / Used / Available Memory
* Network Usage
   * Download / Upload
   * Network Throughput
* System Uptime
   * ระยะเวลาที่เครื่องเปิดใช้งาน
* Number of Processes
   * จำนวน Process ทั้งหมด
   * จำนวน Running Process

Process Information เพิ่มเติม
* Process Thread Count
   * จำนวน Thread ของแต่ละ Process
* Process Memory Usage
   * RAM ที่แต่ละ Process ใช้อยู่
* Process CPU Usage
   * แสดงและ Sort Process ตาม CPU Usage เพื่อดู Process ที่ใช้ CPU สูงได้ง่าย

ฟีเจอร์ด้าน System Resource เช่น CPU, RAM, Disk, GPU และ Network ให้เน้นทดสอบบน Windows
เนื่องจากข้อมูลบางอย่างขึ้นอยู่กับ OS และ Hardware/Driver ของเครื่อง
```

### ✅ Requirement Checklist (ทุกข้อต้องมีครบ — ใช้ติ๊กตอนส่งงาน)

| ID | Requirement | Field ใน model | วิธีดึง | ทดสอบ |
|---|---|---|---|---|
| S1 | CPU Usage (%) ทั้งเครื่อง | `cpu.usagePercent` | OSHI `getSystemCpuLoadBetweenTicks` | เทียบ Task Manager > Performance > CPU |
| S2.1 | Total RAM | `memory.totalBytes` | `GlobalMemory.getTotal()` | Task Manager > Memory |
| S2.2 | Used RAM | `memory.usedBytes` | total − available | Task Manager > Memory "In use" |
| S2.3 | Available RAM | `memory.availableBytes` | `GlobalMemory.getAvailable()` | Task Manager > Memory "Available" |
| S3.1 | Disk ที่ใช้ / เหลือ (ทุก drive) | `disks[].usedBytes`, `freeBytes`, `totalBytes`, `usedPercent` | `FileSystem.getFileStores()` | เทียบ This PC (C:, D:, …) |
| S3.2 | Disk Read / Write | `diskIo.readBytesPerSec`, `writeBytesPerSec` | `HWDiskStore` delta / เวลา | copy ไฟล์ใหญ่ เทียบ Task Manager > Disk |
| S4.1 | GPU Usage (%) | `gpus[].usagePercent` | PDH `\GPU Engine(*)\Utilization Percentage` | เปิดวิดีโอ/เกม เทียบ Task Manager > GPU |
| S4.2 | VRAM Usage | `gpus[].vramUsedBytes`, `vramTotalBytes` | PDH `\GPU Adapter Memory(*)\Dedicated Usage` + OSHI `getVRam()` | Task Manager > GPU "Dedicated GPU memory" |
| S5.1 | CPU Model | `cpu.model` | `getProcessorIdentifier().getName()` | Task Manager > CPU (มุมขวาบน) |
| S5.2 | จำนวน Core / Logical Processor | `cpu.physicalCores`, `cpu.logicalCores` | `getPhysicalProcessorCount()`, `getLogicalProcessorCount()` | Task Manager "Cores" / "Logical processors" |
| S5.3 | CPU Frequency (ถ้าดึงได้) | `cpu.maxFreqHz`, `cpu.currentFreqHz` (nullable) | `getMaxFreq()`, `getCurrentFreq()` | Task Manager "Base speed" / "Speed" |
| S6 | System Memory Information: Total / Used / Available | `memory.*` (ชุดเดียวกับ S2) **+ เพิ่ม** `memory.virtualTotalBytes`, `memory.virtualUsedBytes` (Page file / Committed) | `GlobalMemory.getVirtualMemory()` | Task Manager > Memory "Committed" |
| S7.1 | Network Download / Upload | `network.downloadBytesPerSec`, `uploadBytesPerSec` | `NetworkIF` delta / เวลา (รวมทุก interface ที่ up, ไม่รวม loopback) | ดาวน์โหลดไฟล์ เทียบ Task Manager > Ethernet/Wi-Fi |
| S7.2 | Network Throughput | `network.totalThroughputBytesPerSec` (= down + up) และ `network.totalBytesReceived`, `totalBytesSent` (สะสมตั้งแต่เปิดเครื่อง) | `NetworkIF.getBytesRecv/Sent()` | เทียบ Task Manager |
| S8 | System Uptime | `uptimeSeconds` (+ format `2d 03:14:05`) | `OperatingSystem.getSystemUptime()` | Task Manager > CPU "Up time" |
| S9.1 | จำนวน Process ทั้งหมด | `processCount` | `OperatingSystem.getProcessCount()` | Task Manager > CPU "Processes" |
| S9.2 | จำนวน Running Process | `runningProcessCount` | นับ `OSProcess.getState() == RUNNING` (หรือ `ProcessHandle.isAlive()` ถ้า Windows ให้ state ไม่ละเอียด — ระบุใน doc ว่านิยามอย่างไร) | เทียบจำนวนในตาราง |
| P1 | Thread Count ของแต่ละ Process | `ProcessInfo.threadCount` + column **Threads** | `OSProcess.getThreadCount()` | Task Manager > Details (เพิ่ม column Threads) |
| P2 | Memory ของแต่ละ Process | `ProcessInfo.memoryBytes` + column **Memory** | `OSProcess.getResidentSetSize()` (Working Set) | Task Manager > Details "Memory" / Working set |
| P3 | แสดง + Sort ตาม CPU Usage | column **CPU Usage** (มีอยู่แล้ว) | comparator แบบตัวเลข, sort มาก→น้อยได้ | process ที่กิน CPU สูงต้องขึ้นบนสุด |
| T1 | เน้นทดสอบบน Windows | — | — | ทำ Manual test checklist (หัวข้อ 5) บน Windows จริง |

> ทุก field ต้องแสดงผลได้จริงใน UI (อย่างน้อยใน placeholder ตามหัวข้อ 4) — ไม่ใช่แค่มีใน model
> ถ้าข้อไหนดึงไม่ได้บนบางเครื่อง ให้แสดง `N/A` และระบุเหตุผลใน `docs/SYSTEM_MONITORING.md`

### ❌ ไม่ต้องทำ (ส่วนของเพื่อน)

- Redesign layout, Card/Panel สวย ๆ, theme, CSS, progress bar/graph สวย ๆ
- รื้อโครงสร้าง UI เดิมใน `App.java`

> แตะ `App.java` ให้ **น้อยที่สุด** เพื่อลด merge conflict กับคนทำ UI — เพิ่มเฉพาะจุด hook-up ที่จำเป็น (ดูหัวข้อ 4)

---

## 3. แนวทางเทคนิค

### 3.1 Library หลัก: OSHI

แนะนำใช้ **OSHI** (`com.github.oshi:oshi-core`, เวอร์ชัน 6.x ล่าสุดที่ stable — เช็คจาก Maven Central) เพราะ cross-platform (ทำงานทั้ง macOS ตอน dev และ Windows ตอน target) และครอบคลุมเกือบทุก metric

- ⚠️ OSHI ใช้ JNA — ตรวจ `pom.xml` ว่า version ของ `jna` / `jna-platform` ที่ประกาศไว้ไม่ชนกับที่ OSHI ต้องการ (ให้ใช้ version เดียวกัน หรือเอา explicit JNA dependency ออกถ้า OSHI ดึงมาให้แล้ว) และต้องไม่ทำให้ `WindowsProcessMetrics.java` พัง
- ถ้าโปรเจกต์มี `module-info.java` ต้องเพิ่ม `requires com.github.oshi;` ด้วย

| Metric | OSHI API (แนวทาง) |
|---|---|
| CPU Usage ทั้งเครื่อง | `CentralProcessor.getSystemCpuLoadBetweenTicks(prevTicks)` — เก็บ ticks รอบก่อนไว้ |
| CPU Model / Cores | `getProcessorIdentifier().getName()`, `getPhysicalProcessorCount()`, `getLogicalProcessorCount()` |
| CPU Frequency | `getMaxFreq()`, `getCurrentFreq()` (บน Windows current freq อาจไม่แม่น — ถ้าไม่ได้ค่าให้แสดง base/max แทน) |
| Memory | `GlobalMemory.getTotal()`, `getAvailable()` → used = total − available |
| Disk Space | `FileSystem.getFileStores()` → `getTotalSpace()`, `getUsableSpace()` |
| Disk Read/Write | `HWDiskStore.getReadBytes()` / `getWriteBytes()` + `updateAttributes()` → คำนวณ delta / เวลา |
| Network | `NetworkIF.getBytesRecv()` / `getBytesSent()` + `updateAttributes()` → delta / เวลา (ข้าม loopback / interface ที่ down), throughput รวม = download + upload |
| Uptime | `OperatingSystem.getSystemUptime()` |
| Process count | `OperatingSystem.getProcessCount()` และนับ `OSProcess.getState() == RUNNING` |
| Thread Count | `OSProcess.getThreadCount()` |
| Process Memory | `OSProcess.getResidentSetSize()` (= Working Set บน Windows) |
| GPU Name / VRAM Total | `HardwareAbstractionLayer.getGraphicsCards()` → `getName()`, `getVRam()` |

### 3.2 GPU Usage (%) และ VRAM Used — Windows only

OSHI **ไม่มี** GPU usage % และ VRAM used ต้องดึงเองจาก Windows Performance Counters (PDH):

- GPU Usage: `\GPU Engine(*)\Utilization Percentage` — รวมค่าของ engine ประเภท `engtype_3D` (หรือใช้ค่า max ของ engine ต่อ adapter แบบเดียวกับ Task Manager)
- VRAM Used: `\GPU Adapter Memory(*)\Dedicated Usage`
- ⚠️ ใช้ **`PdhAddEnglishCounter`** ไม่ใช่ `PdhAddCounter` เพราะชื่อ counter ถูกแปลภาษาตาม Windows locale (เช่น Windows ภาษาไทย)
- ทำผ่าน JNA (`com.sun.jna.platform.win32.Pdh`) หรือ helper ของ OSHI ถ้ามีให้ใช้ — ให้ verify ว่า API มีอยู่จริงใน version ที่ใช้
- Fallback: ถ้าดึงไม่ได้ (ไม่ใช่ Windows, ไม่มี driver, VM, ไม่มี counter) → คืนค่า "ไม่รองรับ / N/A" **ห้าม throw จนโปรแกรมพัง**
- ห้ามใช้ `nvidia-smi` เป็นวิธีหลัก (ใช้ได้แค่ NVIDIA)

### 3.3 Process metrics เพิ่มเติม

- `ProcessService` ใช้ `ProcessHandle` อยู่แล้ว → ให้ดึง `OSProcess` list จาก OSHI **ครั้งเดียวต่อรอบ refresh** แล้วทำ `Map<Long pid, OSProcess>` เพื่อ merge thread count + memory เข้า `ProcessInfo` (อย่า query ทีละ PID)
- Process ที่ไม่มีสิทธิ์อ่าน (system process) → ใส่ค่า default เช่น `-1` / แสดง `N/A` ไม่ให้ crash
- อย่าเปลี่ยนวิธีคำนวณ CPU Usage / CPU Time เดิมถ้าไม่จำเป็น (ถ้าจะเปลี่ยนให้ถามก่อน)

### 3.4 Threading (ห้าม UI ค้าง)

- การ sample ทั้งหมดทำบน background thread (`ScheduledExecutorService`, daemon thread) — **ห้ามทำบน JavaFX Application Thread**
- System metrics sample ทุก 1 วินาที (rate ต่าง ๆ คำนวณจาก delta ระหว่าง 2 sample โดยใช้เวลาจริงจาก `System.nanoTime()`)
- ส่งผลเข้า UI ผ่าน `Platform.runLater(...)` เท่านั้น
- Model ที่ส่งออกเป็น immutable snapshot (แนะนำ Java `record`)
- ต้องมี `stop()` / shutdown executor ตอนปิดโปรแกรม (`Application.stop()`)

---

## 4. โครงสร้างไฟล์ที่แนะนำ

แยก logic ออกจาก `App.java` (ซึ่งใหญ่เกินไปแล้ว) — **ไม่ใส่ logic monitoring ลงใน App.java**

```
src/main/java/com/example/processmanager/
├── monitor/
│   ├── SystemMonitorService.java    // start/stop, scheduler, sample ทุก 1 วิ, แจ้ง listener
│   ├── CpuMonitor.java              // CPU usage + CPU info
│   ├── MemoryMonitor.java
│   ├── DiskMonitor.java             // space + read/write rate
│   ├── NetworkMonitor.java          // download/upload rate
│   ├── GpuMonitor.java              // interface/facade
│   └── WindowsGpuMetrics.java       // PDH implementation (Windows only, fallback ถ้าไม่ใช่)
├── model/
│   ├── ProcessInfo.java             // + threadCount, memoryBytes
│   └── system/
│       ├── SystemSnapshot.java      // record รวมทุกอย่างใน 1 รอบ
│       ├── CpuInfo.java
│       ├── MemoryInfo.java
│       ├── DiskInfo.java
│       ├── GpuInfo.java
│       └── NetworkInfo.java
└── util/
    └── FormatUtils.java             // bytes → "1.2 GB", bytes/s → "350 KB/s", uptime → "2d 03:14:05", percent
```

### API ที่ส่งให้คนทำ UI ใช้ (contract)

ออกแบบให้ฝั่ง UI แค่ subscribe แล้ว bind ได้เลย เช่น:

```java
SystemMonitorService monitor = new SystemMonitorService();
monitor.addListener(snapshot -> { /* เรียกบน FX thread แล้ว */ });
// หรือ
ReadOnlyObjectProperty<SystemSnapshot> snapshotProperty();
monitor.start();
monitor.stop();
```

`SystemSnapshot` ควรมีอย่างน้อย:

```
timestamp
cpu:      usagePercent, model, physicalCores, logicalCores, maxFreqHz, currentFreqHz (nullable)
memory:   totalBytes, usedBytes, availableBytes, usedPercent, virtualTotalBytes, virtualUsedBytes
disks:    List<{ name/mount, totalBytes, usedBytes, freeBytes, usedPercent }>
diskIo:   readBytesPerSec, writeBytesPerSec
gpus:     List<{ name, usagePercent (nullable), vramUsedBytes (nullable), vramTotalBytes, supported }>
network:  downloadBytesPerSec, uploadBytesPerSec, totalThroughputBytesPerSec, totalBytesReceived, totalBytesSent, totalThroughputBytesPerSec (= down + up)
uptimeSeconds
processCount, runningProcessCount
```

> ค่าที่ดึงไม่ได้ให้เป็น `null` / `OptionalDouble` / flag `supported=false` — UI จะเป็นคนตัดสินใจแสดง `N/A`

### Hook-up ขั้นต่ำใน App.java (ทำได้เท่านี้)

1. สร้าง / start / stop `SystemMonitorService`
2. เพิ่ม Table column **Threads** และ **Memory** (แสดงผ่าน `FormatUtils`, sort เป็นตัวเลข)
3. เพิ่ม placeholder แบบเรียบง่ายที่สุด (เช่น `Label`/`VBox` ข้อความธรรมดาด้านบนหรือข้างตาราง) เพื่อแสดงค่าจาก `SystemSnapshot` ให้ทดสอบได้ — คนทำ UI จะเอาไปทำเป็น Card สวย ๆ ภายหลัง
4. ใส่ comment `// TODO(UI): ...` ตรงจุดที่ฝั่ง UI ต้องมาแทนที่

---

## 5. Acceptance Criteria

- [ ] `./mvnw clean package` และ `./mvnw javafx:run` ผ่านทั้งบน macOS และ Windows (`mvnw.cmd`)
- [ ] **ทุกข้อใน Requirement Checklist (S1–S9.2, P1–P3, T1) ทำครบและแสดงผลได้จริง** — ตอนส่งงานให้รายงานสถานะทีละ ID
- [ ] แสดง CPU %, RAM (Total/Used/Available), System Memory (รวม Virtual/Committed), Disk space ทุก drive, Disk R/W, Network Down/Up + Throughput, Uptime, Process count / Running count
- [ ] แสดง CPU Model, Core/Logical Processor, Frequency (ถ้าได้)
- [ ] บน Windows: แสดง GPU name, GPU %, VRAM used/total — ค่าใกล้เคียง Task Manager (±5%)
- [ ] บน macOS / เครื่องที่ไม่รองรับ GPU counter: แสดง N/A และโปรแกรมไม่ crash
- [ ] Process table มี Threads และ Memory, sort ตามตัวเลขถูกต้อง; sort CPU Usage มาก→น้อยได้
- [ ] UI ไม่ค้างระหว่าง refresh / auto refresh (ไม่มีงานหนักบน FX thread)
- [ ] ปิดโปรแกรมแล้ว background thread หยุดจริง (process java ไม่ค้าง)
- [ ] `App.java` ไม่ใหญ่ขึ้นมาก — logic อยู่ใน package `monitor` / `model.system` / `util`
- [ ] มี unit test สำหรับ `FormatUtils` และการคำนวณ rate จาก delta (ไม่ต้องพึ่ง hardware จริง)

### Manual test checklist บน Windows (เทียบกับ Task Manager)

- CPU % ทั้งเครื่อง, RAM used, Disk R/W ตอน copy ไฟล์ใหญ่, Network ตอนดาวน์โหลด, GPU % ตอนเปิดวิดีโอ/เกม, Thread count และ Memory ของ process เช่น `chrome.exe`, `java.exe`

---

## 6. ลำดับการทำงาน (Steps)

1. **Setup** — เพิ่ม OSHI ใน `pom.xml`, แก้ JNA version ให้ไม่ชน, build ผ่าน
2. **Models** — สร้าง records ใน `model/system/` + `FormatUtils` + unit tests
3. **CPU / Memory / Uptime / Process count** — `CpuMonitor`, `MemoryMonitor`, `SystemMonitorService` (scheduler + listener)
4. **Disk + Network** — space และ rate จาก delta
5. **Process metrics** — เพิ่ม `threadCount`, `memoryBytes` ใน `ProcessInfo` และ merge ใน `ProcessService`
6. **GPU** — `WindowsGpuMetrics` ผ่าน PDH (English counter) + fallback
7. **Hook-up ขั้นต่ำ** ใน `App.java` (หัวข้อ 4)
8. **Cleanup** — shutdown executor, ตรวจ exception handling, เขียน `docs/SYSTEM_MONITORING.md` สั้น ๆ อธิบาย API (`SystemMonitorService`, `SystemSnapshot`) ให้คนทำ UI ใช้

หลังจบแต่ละ step: สรุปสั้น ๆ ว่าแก้ไฟล์อะไร และมีอะไรที่ต้องไปทดสอบบน Windows จริง

---

## 7. ข้อห้าม / ข้อควรระวัง

- ห้าม redesign UI / ห้ามเปลี่ยน layout เดิมนอกจาก hook-up ขั้นต่ำ
- ห้ามทำงาน I/O หรือ native call บน JavaFX Application Thread
- ห้ามใช้คำสั่ง shell (`wmic`, `tasklist`, PowerShell) เป็นวิธีหลัก — ช้าและ `wmic` ถูก deprecate แล้ว (ใช้ได้แค่เป็น fallback สุดท้าย)
- ห้ามสร้าง `SystemInfo` (OSHI) ใหม่ทุกรอบ — สร้างครั้งเดียวแล้ว reuse
- ทุก metric ต้อง fail แบบ graceful (log + คืน N/A) ไม่ให้ทั้งโปรแกรมพังเพราะ metric เดียว
- ถ้าต้องตัดสินใจที่กระทบโค้ดเดิมหรือฝั่ง UI ให้ถามก่อน
