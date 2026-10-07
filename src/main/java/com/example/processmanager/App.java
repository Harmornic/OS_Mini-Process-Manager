package com.example.processmanager;

import com.example.processmanager.model.ProcessEvent;
import com.example.processmanager.model.ProcessInfo;
import com.example.processmanager.model.ProcessTreeNode;
import com.example.processmanager.monitor.SystemMonitorService;
import com.example.processmanager.service.ProcessService;
import com.example.processmanager.util.FormatUtils;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class App extends Application {

    private final ProcessService processService =
            new ProcessService();

    private final TableView<ProcessInfo> table =
            new TableView<>();

    private final Label totalLabel =
            new Label();

    private final SystemMonitorService systemMonitor =
            new SystemMonitorService();

    // TODO(UI): temporary plain-text placeholder; replace with CPU / RAM / Disk / GPU / Network cards.
    private final Label systemInfoLabel =
            new Label("Loading system information...");

    private final ObservableList<ProcessInfo> processList =
            FXCollections.observableArrayList();

    private FilteredList<ProcessInfo> filteredProcesses;

    private SortedList<ProcessInfo> sortedProcesses;

    private Timeline autoRefreshTimeline;

    private Timeline cpuUsageTimeline;

    private final Map<Long, java.time.Duration> previousCpuTimes =
            new HashMap<>();

    private final Map<Long, ProcessInfo> previousProcesses =
            new HashMap<>();

    private final List<ProcessEvent> processEvents =
            new ArrayList<>();

    private long previousCpuSampleTime;

    private TextField searchField;

    private ComboBox<String> statusFilter;

    private static final DateTimeFormatter START_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss"
            ).withZone(
                    ZoneId.of("Asia/Bangkok")
            );

    @Override
    public void start(Stage stage) {

        Label title =
                new Label("Mini Process Manager");

        title.setStyle(
                "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;"
        );

        searchField =
                new TextField();

        searchField.setPromptText(
                "Search process..."
        );

        searchField.setPrefWidth(250);

        statusFilter =
                new ComboBox<>();

        statusFilter.getItems().addAll(
                "All",
                "Running",
                "Terminated"
        );

        statusFilter.setValue("All");

        TableColumn<ProcessInfo, Long> pidColumn =
                new TableColumn<>("PID");

        pidColumn.setCellValueFactory(
                new PropertyValueFactory<>("pid")
        );

        pidColumn.setPrefWidth(80);

        TableColumn<ProcessInfo, Long> parentPidColumn =
                new TableColumn<>("Parent PID");

        parentPidColumn.setCellValueFactory(
                new PropertyValueFactory<>("parentPid")
        );

        parentPidColumn.setPrefWidth(100);

        TableColumn<ProcessInfo, String> nameColumn =
                new TableColumn<>("Name");

        nameColumn.setCellValueFactory(
                new PropertyValueFactory<>("name")
        );

        nameColumn.setPrefWidth(150);

        TableColumn<ProcessInfo, String> commandColumn =
                new TableColumn<>("Command / Path");

        commandColumn.setCellValueFactory(
                new PropertyValueFactory<>("command")
        );

        commandColumn.setPrefWidth(300);

        TableColumn<ProcessInfo, String> statusColumn =
                new TableColumn<>("Status");

        statusColumn.setCellValueFactory(
                new PropertyValueFactory<>("status")
        );

        statusColumn.setPrefWidth(100);

        TableColumn<ProcessInfo, String> cpuTimeColumn =
                new TableColumn<>("CPU Time");

        cpuTimeColumn.setCellValueFactory(
                cellData -> {

                    java.time.Duration cpuTime =
                            cellData.getValue()
                                    .getCpuTime();

                    return new SimpleStringProperty(
                            formatDuration(cpuTime)
                    );
                }
        );

        cpuTimeColumn.setPrefWidth(120);

        // Value is the number itself so sorting is numeric; text formatting is done by the cell.
        TableColumn<ProcessInfo, Double> cpuUsageColumn =
                new TableColumn<>("CPU Usage");

        cpuUsageColumn.setCellValueFactory(
                cellData ->
                        new SimpleObjectProperty<>(
                                cellData.getValue()
                                        .getCpuUsage()
                        )
        );

        cpuUsageColumn.setCellFactory(
                column -> formattedCell(
                        cpuUsage -> String.format(
                                "%.3f%%",
                                cpuUsage
                        )
                )
        );

        cpuUsageColumn.setPrefWidth(100);

        TableColumn<ProcessInfo, Integer> threadsColumn =
                new TableColumn<>("Threads");

        threadsColumn.setCellValueFactory(
                cellData ->
                        new SimpleObjectProperty<>(
                                cellData.getValue()
                                        .getThreadCount()
                        )
        );

        threadsColumn.setCellFactory(
                column -> formattedCell(
                        threads -> threads < 0
                                ? FormatUtils.NA
                                : String.valueOf(threads)
                )
        );

        threadsColumn.setPrefWidth(80);

        TableColumn<ProcessInfo, Long> memoryColumn =
                new TableColumn<>("Memory");

        memoryColumn.setCellValueFactory(
                cellData ->
                        new SimpleObjectProperty<>(
                                cellData.getValue()
                                        .getMemoryBytes()
                        )
        );

        memoryColumn.setCellFactory(
                column -> formattedCell(
                        FormatUtils::bytes
                )
        );

        memoryColumn.setPrefWidth(100);

        TableColumn<ProcessInfo, Integer> treeColumn =
                new TableColumn<>("Tree");

        treeColumn.setCellValueFactory(
                cellData ->
                        new SimpleObjectProperty<>(
                                countDescendants(
                                        cellData.getValue()
                                                .getPid()
                                )
                        )
        );

        treeColumn.setPrefWidth(80);

        TableColumn<ProcessInfo, String> startTimeColumn =
                new TableColumn<>("Start Time");

        startTimeColumn.setCellValueFactory(
                cellData -> {

                    Instant startTime =
                            cellData.getValue()
                                    .getStartTime();

                    return new SimpleStringProperty(
                            startTime == null
                                    ? "Unknown"
                                    : START_TIME_FORMATTER.format(
                                            startTime
                                    )
                    );
                }
        );

        startTimeColumn.setPrefWidth(220);

        table.getColumns().addAll(
                pidColumn,
                parentPidColumn,
                nameColumn,
                commandColumn,
                statusColumn,
                cpuTimeColumn,
                cpuUsageColumn,
                threadsColumn,
                memoryColumn,
                treeColumn,
                startTimeColumn
        );

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        filteredProcesses =
                new FilteredList<>(
                        processList,
                        process -> true
                );

        sortedProcesses =
                new SortedList<>(
                        filteredProcesses
                );

        sortedProcesses.comparatorProperty()
                .bind(
                        table.comparatorProperty()
                );

        table.setItems(sortedProcesses);

        searchField.textProperty()
                .addListener(
                        (
                                observable,
                                oldValue,
                                newValue
                        ) -> applySearch()
                );

        statusFilter.valueProperty()
                .addListener(
                        (
                                observable,
                                oldValue,
                                newValue
                        ) -> applySearch()
                );

        Button refreshButton =
                new Button("Refresh");

        refreshButton.setOnAction(
                event -> refreshProcesses()
        );

        Button killButton =
                new Button("Kill Process");

        killButton.setOnAction(
                event -> killSelectedProcess()
        );

        Button detailsButton =
                new Button("Process Details");

        detailsButton.setOnAction(
                event -> showProcessDetails()
        );

        Button treeButton =
                new Button("Process Tree");

        treeButton.setOnAction(
                event -> showProcessTree()
        );

        Button historyButton =
                new Button("Process History");

        historyButton.setOnAction(
                event -> showProcessHistory()
        );

        HBox controls =
                new HBox(
                        10,
                        searchField,
                        statusFilter,
                        refreshButton,
                        detailsButton,
                        treeButton,
                        historyButton,
                        killButton
                );

        controls.setPadding(
                new Insets(10)
        );

        CheckBox autoRefresh =
                new CheckBox("Auto Refresh");

        Label refreshInfo =
                new Label(
                        "Process List: 5 sec | CPU Usage: 1 sec"
                );

        HBox refreshControls =
                new HBox(
                        10,
                        autoRefresh,
                        refreshInfo
                );

        refreshControls.setPadding(
                new Insets(
                        0,
                        10,
                        10,
                        10
                )
        );

        autoRefreshTimeline =
                new Timeline(
                        new KeyFrame(
                                Duration.seconds(5),
                                event -> refreshProcesses()
                        )
                );

        autoRefreshTimeline.setCycleCount(
                Timeline.INDEFINITE
        );

        cpuUsageTimeline =
                new Timeline(
                        new KeyFrame(
                                Duration.seconds(1),
                                event -> sampleCpuUsage()
                        )
                );

        cpuUsageTimeline.setCycleCount(
                Timeline.INDEFINITE
        );

        autoRefresh.selectedProperty()
                .addListener(
                        (
                                observable,
                                oldValue,
                                selected
                        ) -> {

                            if (selected) {

                                autoRefreshTimeline.play();

                                cpuUsageTimeline.play();

                            } else {

                                autoRefreshTimeline.stop();

                                cpuUsageTimeline.stop();
                            }
                        }
                );

        refreshProcesses();

        systemMonitor.addListener(
                snapshot -> systemInfoLabel.setText(
                        FormatUtils.summary(snapshot)
                )
        );

        systemMonitor.start();

        VBox topSection =
                new VBox(
                        10,
                        title,
                        systemInfoLabel,
                        controls,
                        refreshControls
                );

        topSection.setPadding(
                new Insets(10)
        );

        BorderPane root =
                new BorderPane();

        root.setTop(topSection);

        root.setCenter(table);

        Scene scene =
                new Scene(
                        root,
                        1350,
                        650
                );

        stage.setTitle(
                "Mini Process Manager"
        );

        stage.setScene(scene);

        stage.show();
    }

    private void applySearch() {

        if (filteredProcesses == null) {
            return;
        }

        String searchText =
                searchField == null
                        ? ""
                        : searchField
                                .getText()
                                .trim()
                                .toLowerCase();

        String selectedStatus =
                statusFilter == null
                        ? "All"
                        : statusFilter.getValue();

        filteredProcesses.setPredicate(
                process -> {

                    boolean matchesSearch =
                            searchText.isEmpty()
                                    || process.getName()
                                    .toLowerCase()
                                    .contains(searchText);

                    boolean matchesStatus =
                            selectedStatus == null
                                    || selectedStatus.equals("All")
                                    || process.getStatus()
                                    .equalsIgnoreCase(
                                            selectedStatus
                                    );

                    return matchesSearch
                            && matchesStatus;
                }
        );
    }

    private void refreshProcesses() {

        List<ProcessInfo> rawProcesses =
                processService.getAllProcesses();

        Map<Long, java.time.Duration> currentCpuTimes =
                new HashMap<>();

        List<ProcessInfo> processes =
                rawProcesses.stream()
                        .map(process -> {

                            currentCpuTimes.put(
                                    process.getPid(),
                                    process.getCpuTime()
                            );

                            return process.withCpuUsage(0.0);
                        })
                        .toList();

        detectProcessEvents(processes);

        previousCpuTimes.clear();

        previousCpuTimes.putAll(
                currentCpuTimes
        );

        previousCpuSampleTime =
                System.nanoTime();

        processList.setAll(
                processes
        );

        applySearch();

        totalLabel.setText(
                "Total Processes: "
                        + processes.size()
        );
    }

    private void detectProcessEvents(
            List<ProcessInfo> currentProcesses) {

        Map<Long, ProcessInfo> currentProcessesMap =
                new HashMap<>();

        for (ProcessInfo process :
                currentProcesses) {

            currentProcessesMap.put(
                    process.getPid(),
                    process
            );
        }

        /*
         * First snapshot:
         * Use it as the baseline.
         * Do not mark every existing process as CREATED.
         */
        if (previousProcesses.isEmpty()) {

            previousProcesses.putAll(
                    currentProcessesMap
            );

            return;
        }

        Instant eventTime =
                Instant.now();

        // Detect CREATED processes
        for (ProcessInfo current :
                currentProcesses) {

            if (!previousProcesses.containsKey(
                    current.getPid())) {

                processEvents.add(
                        new ProcessEvent(
                                current.getPid(),
                                current.getName(),
                                ProcessEvent.EventType.CREATED,
                                eventTime
                        )
                );
            }
        }

        // Detect TERMINATED processes
        for (ProcessInfo previous :
                previousProcesses.values()) {

            if (!currentProcessesMap.containsKey(
                    previous.getPid())) {

                processEvents.add(
                        new ProcessEvent(
                                previous.getPid(),
                                previous.getName(),
                                ProcessEvent.EventType.TERMINATED,
                                eventTime
                        )
                );
            }
        }

        previousProcesses.clear();

        previousProcesses.putAll(
                currentProcessesMap
        );
    }

    private void sampleCpuUsage() {

        List<ProcessInfo> rawProcesses =
                processService.getAllProcesses();

        long currentTime =
                System.nanoTime();

        if (previousCpuSampleTime == 0) {

            previousCpuSampleTime =
                    currentTime;

            return;
        }

        double elapsedSeconds =
                (currentTime - previousCpuSampleTime)
                        / 1_000_000_000.0;

        if (elapsedSeconds <= 0) {
            return;
        }

        Map<Long, ProcessInfo> currentProcesses =
                new HashMap<>();

        for (ProcessInfo process :
                rawProcesses) {

            currentProcesses.put(
                    process.getPid(),
                    process
            );
        }

        ObservableList<ProcessInfo> updatedList =
                FXCollections.observableArrayList();

        for (ProcessInfo oldProcess :
                processList) {

            ProcessInfo currentProcess =
                    currentProcesses.get(
                            oldProcess.getPid()
                    );

            if (currentProcess == null) {
                continue;
            }

            java.time.Duration previousCpu =
                    previousCpuTimes.get(
                            currentProcess.getPid()
                    );

            double cpuUsage = 0.0;

            if (previousCpu != null) {

                long cpuNanos =
                        currentProcess
                                .getCpuTime()
                                .toNanos()
                                - previousCpu.toNanos();

                cpuUsage =
                        (cpuNanos / 1_000_000_000.0)
                                / elapsedSeconds
                                * 100.0;

                if (cpuUsage < 0) {
                    cpuUsage = 0;
                }

                if (cpuUsage > 100) {
                    cpuUsage = 100;
                }
            }

            ProcessInfo updatedProcess =
                    currentProcess.withCpuUsage(
                            cpuUsage
                    );

            updatedList.add(
                    updatedProcess
            );
        }

        processList.setAll(
                updatedList
        );

        previousCpuTimes.clear();

        for (ProcessInfo process :
                rawProcesses) {

            previousCpuTimes.put(
                    process.getPid(),
                    process.getCpuTime()
            );
        }

        previousCpuSampleTime =
                currentTime;

        applySearch();
    }

    private void showProcessHistory() {

        Stage historyStage =
                new Stage();

        historyStage.setTitle(
                "Process History"
        );

        TableView<ProcessEvent> historyTable =
                new TableView<>();

        TableColumn<ProcessEvent, String> timeColumn =
                new TableColumn<>("Time");

        timeColumn.setCellValueFactory(
                cellData ->
                        new SimpleStringProperty(
                                cellData.getValue()
                                        .getTimestamp()
                                        .toString()
                        )
        );

        timeColumn.setPrefWidth(230);

        TableColumn<ProcessEvent, String> eventColumn =
                new TableColumn<>("Event");

        eventColumn.setCellValueFactory(
                cellData ->
                        new SimpleStringProperty(
                                cellData.getValue()
                                        .getEventType()
                                        .toString()
                        )
        );

        eventColumn.setPrefWidth(120);

        TableColumn<ProcessEvent, Long> pidColumn =
                new TableColumn<>("PID");

        pidColumn.setCellValueFactory(
                cellData ->
                        new SimpleObjectProperty<>(
                                cellData.getValue()
                                        .getPid()
                        )
        );

        pidColumn.setPrefWidth(100);

        TableColumn<ProcessEvent, String> processColumn =
                new TableColumn<>("Process");

        processColumn.setCellValueFactory(
                cellData ->
                        new SimpleStringProperty(
                                cellData.getValue()
                                        .getProcessName()
                        )
        );

        processColumn.setPrefWidth(220);

        historyTable.getColumns().addAll(
                timeColumn,
                eventColumn,
                pidColumn,
                processColumn
        );

        historyTable.setItems(
                FXCollections.observableArrayList(
                        processEvents
                )
        );

        historyTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        Label historyInfo =
                new Label(
                        "Detected Events: "
                                + processEvents.size()
                );

        Button refreshHistoryButton =
                new Button("Refresh");

        refreshHistoryButton.setOnAction(
                event -> {

                    historyTable.setItems(
                            FXCollections.observableArrayList(
                                    processEvents
                            )
                    );

                    historyInfo.setText(
                            "Detected Events: "
                                    + processEvents.size()
                    );
                }
        );

        HBox controls =
                new HBox(
                        10,
                        refreshHistoryButton,
                        historyInfo
                );

        controls.setPadding(
                new Insets(10)
        );

        BorderPane root =
                new BorderPane();

        root.setTop(controls);

        root.setCenter(historyTable);

        Scene scene =
                new Scene(
                        root,
                        750,
                        500
                );

        historyStage.setScene(scene);

        historyStage.show();
    }

    private void killSelectedProcess() {

        ProcessInfo selected =
                table.getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {

            showAlert(
                    "No Process Selected",
                    "Please select a process first."
            );

            return;
        }

        boolean destroyed =
                ProcessHandle.of(
                        selected.getPid()
                )
                .map(ProcessHandle::destroy)
                .orElse(false);

        if (destroyed) {

            showAlert(
                    "Process",
                    "Process "
                            + selected.getPid()
                            + " terminated."
            );

            refreshProcesses();

        } else {

            showAlert(
                    "Process",
                    "Unable to terminate process "
                            + selected.getPid()
            );
        }
    }

    private void showProcessDetails() {

        ProcessInfo selected =
                table.getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {

            showAlert(
                    "No Process Selected",
                    "Please select a process first."
            );

            return;
        }

        String details =
                "PID: "
                        + selected.getPid()
                        + "\n\n"

                        + "Parent PID: "
                        + selected.getParentPid()
                        + "\n\n"

                        + "Name: "
                        + selected.getName()
                        + "\n\n"

                        + "Command / Path: "
                        + selected.getCommand()
                        + "\n\n"

                        + "Status: "
                        + selected.getStatus()
                        + "\n\n"

                        + "CPU Time: "
                        + formatDuration(
                                selected.getCpuTime()
                        )
                        + "\n\n"

                        + "CPU Usage: "
                        + String.format(
                                "%.3f%%",
                                selected.getCpuUsage()
                        )
                        + "\n\n"

                        + "Start Time: "
                        + (
                                selected.getStartTime() == null
                                        ? "Unknown"
                                        : START_TIME_FORMATTER.format(
                                                selected.getStartTime()
                                        )
                        );

        showAlert(
                "Process Details",
                details
        );
    }

    private void showProcessTree() {

        ProcessInfo selected =
                table.getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {

            showAlert(
                    "No Process Selected",
                    "Please select a process first."
            );

            return;
        }

        Stage treeStage =
                new Stage();

        treeStage.setTitle(
                "Process Genealogy - "
                        + selected.getName()
        );

        TreeItem<ProcessTreeNode> root =
                buildTreeViewRoot(
                        selected.getPid()
                );

        TreeView<ProcessTreeNode> treeView =
                new TreeView<>(root);

        treeView.setShowRoot(true);

        root.setExpanded(true);

        treeView.setCellFactory(
                view -> new TreeCell<>() {

                    @Override
                    protected void updateItem(
                            ProcessTreeNode item,
                            boolean empty) {

                        super.updateItem(
                                item,
                                empty
                        );

                        if (empty || item == null) {

                            setText(null);

                        } else {

                            ProcessInfo process =
                                    item.getProcess();

                            setText(
                                    process.getName()
                                            + " | PID: "
                                            + process.getPid()
                                            + " | CPU: "
                                            + String.format(
                                                    "%.3f%%",
                                                    process.getCpuUsage()
                                            )
                                            + " | CPU Time: "
                                            + formatDuration(
                                                    process.getCpuTime()
                                            )
                                            + " | Status: "
                                            + process.getStatus()
                                            + " | Children: "
                                            + countDirectChildren(
                                                    process.getPid()
                                            )
                            );
                        }
                    }
                }
        );

        BorderPane rootPane =
                new BorderPane();

        rootPane.setCenter(
                treeView
        );

        Scene scene =
                new Scene(
                        rootPane,
                        1000,
                        500
                );

        treeStage.setScene(scene);

        treeStage.show();
    }

    private TreeItem<ProcessTreeNode> buildTreeViewRoot(
            long pid) {

        Map<Long, ProcessTreeNode> nodeMap =
                new HashMap<>();

        for (ProcessInfo process :
                processList) {

            nodeMap.put(
                    process.getPid(),
                    new ProcessTreeNode(process)
            );
        }

        ProcessTreeNode selectedNode =
                nodeMap.get(pid);

        if (selectedNode == null) {

            return new TreeItem<>();
        }

        buildChildren(
                selectedNode,
                nodeMap,
                new HashSet<>()
        );

        return createTreeItem(
                selectedNode
        );
    }

    private void buildChildren(
            ProcessTreeNode parent,
            Map<Long, ProcessTreeNode> nodeMap,
            Set<Long> visited) {

        long parentPid =
                parent.getProcess().getPid();

        if (!visited.add(parentPid)) {
            return;
        }

        for (ProcessInfo process :
                processList) {

            if (process.getParentPid()
                    == parentPid) {

                ProcessTreeNode child =
                        nodeMap.get(
                                process.getPid()
                        );

                if (child == null) {
                    continue;
                }

                parent.addChild(child);

                buildChildren(
                        child,
                        nodeMap,
                        visited
                );
            }
        }
    }

    private TreeItem<ProcessTreeNode> createTreeItem(
            ProcessTreeNode node) {

        TreeItem<ProcessTreeNode> item =
                new TreeItem<>(node);

        for (ProcessTreeNode child :
                node.getChildren()) {

            item.getChildren().add(
                    createTreeItem(child)
            );
        }

        return item;
    }

    private void buildProcessTree(
            long pid,
            int level,
            StringBuilder tree) {

        ProcessInfo process =
                processList.stream()
                        .filter(
                                p -> p.getPid() == pid
                        )
                        .findFirst()
                        .orElse(null);

        if (process == null) {
            return;
        }

        tree.append(
                "  ".repeat(level)
        );

        tree.append(
                process.getName()
        );

        tree.append(
                " (PID "
        );

        tree.append(
                process.getPid()
        );

        tree.append(
                ", "
        );

        tree.append(
                process.getStatus()
        );

        tree.append(
                ")"
        );

        tree.append(
                "\n"
        );

        for (ProcessInfo child :
                processList) {

            if (child.getParentPid()
                    == pid) {

                buildProcessTree(
                        child.getPid(),
                        level + 1,
                        tree
                );
            }
        }
    }

    private int countDirectChildren(
            long pid) {

        int count = 0;

        for (ProcessInfo process :
                processList) {

            if (process.getParentPid()
                    == pid) {

                count++;
            }
        }

        return count;
    }

    private int countDescendants(
            long pid) {

        return countDescendants(
                pid,
                new HashSet<>()
        );
    }

    private int countDescendants(
            long pid,
            Set<Long> visited) {

        if (!visited.add(pid)) {
            return 0;
        }

        int count = 0;

        for (ProcessInfo process :
                processList) {

            if (process.getParentPid()
                    == pid) {

                count++;

                count += countDescendants(
                        process.getPid(),
                        visited
                );
            }
        }

        return count;
    }

    private String formatDuration(
            java.time.Duration duration) {

        if (duration == null) {
            return "Unknown";
        }

        long totalSeconds =
                duration.getSeconds();

        long hours =
                totalSeconds / 3600;

        long minutes =
                (totalSeconds % 3600) / 60;

        long seconds =
                totalSeconds % 60;

        return String.format(
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
        );
    }

    private void showAlert(
            String title,
            String message) {

        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION
                );

        alert.setTitle(title);

        alert.setHeaderText(null);

        alert.setContentText(message);

        alert.showAndWait();
    }

    private static <T> TableCell<ProcessInfo, T> formattedCell(
            java.util.function.Function<T, String> formatter) {

        return new TableCell<>() {

            @Override
            protected void updateItem(T item, boolean empty) {

                super.updateItem(item, empty);

                setText(
                        empty || item == null
                                ? null
                                : formatter.apply(item)
                );
            }
        };
    }

    @Override
    public void stop() {
        systemMonitor.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}
