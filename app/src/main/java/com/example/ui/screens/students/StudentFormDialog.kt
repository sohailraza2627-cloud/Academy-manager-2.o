package com.example.ui.screens.students

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AcademyClass
import com.example.data.local.Student
import com.example.data.local.StudentStatus
import com.example.ui.components.ColoredInitialAvatar
import com.example.ui.components.DatePickerField
import com.example.util.ImageStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormDialog(
    initialStudent: Student? = null,
    classes: List<AcademyClass>,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    val context = LocalContext.current
    val isEdit = initialStudent != null

    var name by rememberSaveable(initialStudent?.id) { mutableStateOf(initialStudent?.name ?: "") }
    var fatherName by rememberSaveable(initialStudent?.id) { mutableStateOf(initialStudent?.fatherName ?: "") }
    var phone by rememberSaveable(initialStudent?.id) { mutableStateOf(initialStudent?.phone ?: "") }
    var address by rememberSaveable(initialStudent?.id) { mutableStateOf(initialStudent?.address ?: "") }
    var rollNo by rememberSaveable(initialStudent?.id) { mutableStateOf(initialStudent?.rollNo ?: "") }
    var selectedClassId by rememberSaveable(initialStudent?.id) {
        mutableStateOf(initialStudent?.classId ?: classes.firstOrNull()?.id ?: 0L)
    }
    var admissionDate by rememberSaveable(initialStudent?.id) {
        mutableStateOf(initialStudent?.admissionDate ?: System.currentTimeMillis())
    }
    var discountStr by rememberSaveable(initialStudent?.id) {
        mutableStateOf(initialStudent?.monthlyDiscount?.takeIf { it > 0L }?.toString() ?: "")
    }
    var photoPath by rememberSaveable(initialStudent?.id) { mutableStateOf(initialStudent?.photoPath) }
    var status by rememberSaveable(initialStudent?.id) {
        mutableStateOf(initialStudent?.status ?: StudentStatus.ACTIVE)
    }

    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var fatherError by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneError by rememberSaveable { mutableStateOf<String?>(null) }
    var rollError by rememberSaveable { mutableStateOf<String?>(null) }
    var classError by rememberSaveable { mutableStateOf<String?>(null) }
    var discountError by rememberSaveable { mutableStateOf<String?>(null) }

    var classMenuExpanded by remember { mutableStateOf(false) }

    val requiredMsg = stringResource(R.string.field_required)
    val invalidAmountMsg = stringResource(R.string.invalid_amount)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val savedPath = ImageStorage.copyUriToInternalStorage(context, it, "student_photos")
            if (savedPath != null) {
                photoPath = savedPath
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(if (isEdit) R.string.edit_student else R.string.add_student)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (classes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_classes_prompt),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Photo picker row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColoredInitialAvatar(
                            name = name.ifBlank { "S" },
                            photoPath = photoPath,
                            size = 56.dp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("pick_student_photo_btn")
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.choose_photo))
                            }
                            if (photoPath != null) {
                                IconButton(onClick = { photoPath = null }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.remove_photo)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameError = null },
                        label = { Text(stringResource(R.string.student_name)) },
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_name_input")
                    )

                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it; fatherError = null },
                        label = { Text(stringResource(R.string.father_name)) },
                        isError = fatherError != null,
                        supportingText = fatherError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_father_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = rollNo,
                            onValueChange = { rollNo = it; rollError = null },
                            label = { Text(stringResource(R.string.roll_no)) },
                            isError = rollError != null,
                            supportingText = rollError?.let { { Text(it) } },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("student_roll_input")
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it; phoneError = null },
                            label = { Text(stringResource(R.string.phone_number)) },
                            isError = phoneError != null,
                            supportingText = phoneError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("student_phone_input")
                        )
                    }

                    // Class Dropdown
                    val selectedClass = classes.find { it.id == selectedClassId }
                    ExposedDropdownMenuBox(
                        expanded = classMenuExpanded,
                        onExpandedChange = { classMenuExpanded = !classMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedClass?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_class)) },
                            isError = classError != null,
                            supportingText = classError?.let { { Text(it) } },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("student_class_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = classMenuExpanded,
                            onDismissRequest = { classMenuExpanded = false }
                        ) {
                            classes.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text(cls.name) },
                                    onClick = {
                                        selectedClassId = cls.id
                                        classError = null
                                        classMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    DatePickerField(
                        label = stringResource(R.string.admission_date),
                        dateMillis = admissionDate,
                        onDateSelected = { admissionDate = it }
                    )

                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it; discountError = null },
                        label = { Text(stringResource(R.string.monthly_discount)) },
                        isError = discountError != null,
                        supportingText = discountError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_discount_input")
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.address)) },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_address_input")
                    )
                }
            }
        },
        confirmButton = {
            if (classes.isNotEmpty()) {
                Button(
                    onClick = {
                        var valid = true
                        if (name.isBlank()) { nameError = requiredMsg; valid = false }
                        if (fatherName.isBlank()) { fatherError = requiredMsg; valid = false }
                        if (phone.isBlank()) { phoneError = requiredMsg; valid = false }
                        if (rollNo.isBlank()) { rollError = requiredMsg; valid = false }
                        if (selectedClassId == 0L || classes.none { it.id == selectedClassId }) {
                            classError = requiredMsg; valid = false
                        }
                        val discount = if (discountStr.isBlank()) 0L else discountStr.trim().toLongOrNull()
                        if (discount == null || discount < 0L) {
                            discountError = invalidAmountMsg; valid = false
                        }
                        if (valid && discount != null) {
                            onSave(
                                Student(
                                    id = initialStudent?.id ?: 0L,
                                    name = name.trim(),
                                    fatherName = fatherName.trim(),
                                    phone = phone.trim(),
                                    address = address.trim(),
                                    classId = selectedClassId,
                                    rollNo = rollNo.trim(),
                                    admissionDate = admissionDate,
                                    photoPath = photoPath,
                                    monthlyDiscount = discount,
                                    status = status,
                                    leftDate = initialStudent?.leftDate,
                                    createdAt = initialStudent?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    modifier = Modifier.testTag("save_student_button")
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
