$path = "c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects\app\src\main\java\com\example\myapplication\monitoring.java"
$content = Get-Content $path -Raw -Encoding UTF8

# 1. Add member variable
$content = $content -replace 'boolean isAutomaticMode = true;', "private com.google.android.material.textfield.TextInputEditText etPatientName;`n    boolean isAutomaticMode = true;"

# 2. Add initialization in onCreate (find toolbar initialization)
$content = $content -replace 'toolbar = findViewById\(R\.id\.toolbar\);', "toolbar = findViewById(R.id.toolbar);`n        etPatientName = findViewById(R.id.etPatientName);"

# 3. Update setupStartButton logic
$oldStartBtn = 'btnStart\.setOnClickListener\(v -> \{`r?`n\s+btnStart\.setEnabled\(false\);'
$newStartBtn = 'btnStart.setOnClickListener(v -> {
                String patientName = etPatientName.getText().toString().trim();
                if (patientName.isEmpty()) {
                    etPatientName.setError("Required");
                    return;
                }
                btnStart.setEnabled(false);'
$content = $content -replace $oldStartBtn, $newStartBtn

# 4. Update core SessionStartRequest to include patientName (in setupStartButton)
$content = $content -replace 'SessionStartRequest req = new SessionStartRequest\(userId, intensity, durationMins\);', 'SessionStartRequest req = new SessionStartRequest(userId, intensity, durationMins, patientName);'

# 5. Update showFinalSummaryDialog to include clinical_summary (in msg.append)
$content = $content -replace 'msg\.append\(data\.hii_index\)\.append\("\\n\\n"\);', 'msg.append(data.hii_index).append("\n\n");`n        if (data.clinical_summary != null) {`n            msg.append("Clinical Insight:\n").append(data.clinical_summary).append("\n\n");`n        }'

Set-Content $path $content -Encoding UTF8
Write-Host "✅ monitoring.java updated successfully."
