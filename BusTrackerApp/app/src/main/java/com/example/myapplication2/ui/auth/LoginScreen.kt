package com.example.myapplication2.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    selectedRole: UserRole,
    onRoleSelected: (UserRole) -> Unit,
    onLoginSubmitted: (role: UserRole, field1: String, field2: String, busCategory: String) -> Unit,
    onNavigateToSignup: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var field1 by remember(selectedRole) { mutableStateOf("") }
    var field2 by remember(selectedRole) { mutableStateOf("") }
    var selectedBusCategory by remember { mutableStateOf("KTCL bus") }
    var busCategoryExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val busCategories = listOf("KTCL bus", "Private bus")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selectedRole == UserRole.DRIVER) Icons.Default.DirectionsBus else Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Sign in as ${selectedRole.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Role Selector Toggle
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            UserRole.entries.forEachIndexed { index, role ->
                SegmentedButton(
                    selected = selectedRole == role,
                    onClick = { onRoleSelected(role) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = UserRole.entries.size),
                    icon = {}
                ) {
                    Text(role.label)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedRole == UserRole.DRIVER) {
            // Driver Role: Bus ID, Phone Number & Bus Category Dropdown
            OutlinedTextField(
                value = field1,
                onValueChange = { field1 = it },
                label = { Text("Bus ID (e.g. GA-03-X-1001)") },
                leadingIcon = { Icon(Icons.Default.DirectionsBus, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = field2,
                onValueChange = { field2 = it },
                label = { Text("Phone Number (e.g. +919876543210)") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bus Category Dropdown
            ExposedDropdownMenuBox(
                expanded = busCategoryExpanded,
                onExpandedChange = { busCategoryExpanded = !busCategoryExpanded }
            ) {
                OutlinedTextField(
                    value = selectedBusCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Bus Category") },
                    leadingIcon = { Icon(Icons.Default.DirectionsBus, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = busCategoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = busCategoryExpanded,
                    onDismissRequest = { busCategoryExpanded = false }
                ) {
                    busCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selectedBusCategory = category
                                busCategoryExpanded = false
                            }
                        )
                    }
                }
            }
        } else {
            // Passenger Role: Email & Password / Guest Entry
            OutlinedTextField(
                value = field1,
                onValueChange = { field1 = it },
                label = { Text("Email or Phone") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = field2,
                onValueChange = { field2 = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Inline Error Banner
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val isSubmitEnabled = if (selectedRole == UserRole.DRIVER) {
            field1.isNotBlank() && field2.isNotBlank() && !isLoading
        } else {
            !isLoading // Allow passengers to proceed directly or with credentials
        }

        val mappedCategory = if (selectedBusCategory.contains("Private", ignoreCase = true)) "Private" else "KTCL"

        Button(
            onClick = {
                onLoginSubmitted(selectedRole, field1, field2, mappedCategory)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = isSubmitEnabled
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (selectedRole == UserRole.DRIVER) "Verify & Login Driver" else "Continue as Passenger",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Don't have an account?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onNavigateToSignup) {
                Text("Sign Up", fontWeight = FontWeight.Bold)
            }
        }
    }
}
