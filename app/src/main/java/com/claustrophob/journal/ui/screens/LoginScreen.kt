package com.claustrophob.journal.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.R
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.Res
import com.claustrophob.journal.data.net.CityDto
import com.claustrophob.journal.data.store.SessionStore
import com.claustrophob.journal.ui.components.JCard
import com.claustrophob.journal.ui.components.RowDivider
import com.claustrophob.journal.ui.components.StatusBanner
import com.claustrophob.journal.ui.journalViewModel
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.util.LoginHints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val cities: List<CityDto> = emptyList(),
    val cityId: Int = 0,
    val username: String = "",
    val loadingCities: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    // Техническая причина, её видно по кнопке "Подробности".
    val errorDetail: String? = null,
    // Филиалы того же города, показываем если не пустило.
    val siblingCities: List<CityDto> = emptyList(),
)

class LoginViewModel(
    private val repository: JournalRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(cityId = session.lastCityId, username = session.lastUsername),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        loadCities()
    }

    fun loadCities() {
        _state.value = _state.value.copy(loadingCities = true, error = null)
        viewModelScope.launch {
            when (val result = repository.cities()) {
                is Res.Ok -> _state.value = _state.value.copy(
                    cities = result.data.sortedBy { it.name },
                    loadingCities = false,
                )

                is Res.Err -> _state.value = _state.value.copy(
                    loadingCities = false,
                    error = result.message,
                )
            }
        }
    }

    fun onCityChange(id: Int) {
        _state.value = _state.value.copy(cityId = id, error = null, siblingCities = emptyList())
    }

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun dismissError() {
        _state.value =
            _state.value.copy(error = null, errorDetail = null, siblingCities = emptyList())
    }

    // В больших городах филиалов несколько: "Южно-Сахалинск", "... Колледж",
    // "... Школа". На чужой филиал сервер отвечает тем же "неверный логин или
    // пароль", так что собираем соседей по первому слову — вдруг человек ошибся.
    private fun siblingsOf(cityId: Int): List<CityDto> {
        val cities = _state.value.cities
        val current = cities.firstOrNull { it.id == cityId } ?: return emptyList()
        val root = current.name.substringBefore(' ').trim()
        if (root.length < 4) return emptyList()
        return cities
            .filter { it.id != cityId && it.name.startsWith(root, ignoreCase = true) }
            .take(6)
    }

    fun signIn(password: String, onSuccess: () -> Unit) {
        val current = _state.value
        if (current.cityId == 0) {
            _state.value = current.copy(error = "Выберите город")
            return
        }
        if (current.username.isBlank() || password.isBlank()) {
            _state.value = current.copy(error = "Введите логин и пароль")
            return
        }
        _state.value = current.copy(submitting = true, error = null, errorDetail = null)
        viewModelScope.launch {
            val failure = repository.signIn(current.cityId, current.username, password)
            if (failure == null) {
                _state.value = _state.value.copy(submitting = false)
                onSuccess()
                return@launch
            }
            _state.value = _state.value.copy(
                submitting = false,
                error = failure.message,
                errorDetail = failure.detail,
                // Предлагать другой филиал есть смысл, только если отказал сервер.
                siblingCities = if (failure.credentialsLikely) siblingsOf(current.cityId)
                else emptyList(),
            )
        }
    }
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val viewModel: LoginViewModel = journalViewModel { container: AppContainer ->
        LoginViewModel(container.repository, container.session)
    }
    val state by viewModel.state.collectAsState()

    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var detailsShown by remember { mutableStateOf(false) }
    var cityMenuOpen by remember { mutableStateOf(false) }
    var cityQuery by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val selectedCityName = remember(state.cities, state.cityId) {
        state.cities.firstOrNull { it.id == state.cityId }?.name
            ?: if (state.cityId != 0) "Филиал #${state.cityId}" else ""
    }

    val layoutSuspect = LoginHints.fixableLayout(state.username) ||
        LoginHints.fixableLayout(password)
    val spaceSuspect = LoginHints.hasEdgeSpace(state.username) || LoginHints.hasEdgeSpace(password)

    val submit = {
        keyboard?.hide()
        detailsShown = false
        viewModel.signIn(password) { onSignedIn() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            AppLogo()
            Spacer(Modifier.height(20.dp))
            Text(text = "Журнал", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Личный кабинет студента\nКомпьютерной Академии TOP",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            JCard(contentPadding = PaddingValues(20.dp)) {
                OutlinedTextField(
                    value = selectedCityName,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Город / филиал") },
                    leadingIcon = { Icon(Icons.Rounded.LocationCity, null) },
                    trailingIcon = {
                        if (state.loadingCities) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.ArrowDropDown, null)
                        }
                    },
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors().copy(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FieldShape)
                        .clickable(enabled = !state.loadingCities) {
                            cityQuery = ""
                            cityMenuOpen = true
                        },
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Логин") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(
                        // Иначе клавиатура поднимает первую букву и логин не совпадает.
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (passwordVisible) "Скрыть" else "Показать",
                            )
                        }
                    },
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors(),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.error != null) Spacer(Modifier.height(14.dp))
                StatusBanner(
                    message = state.error,
                    onDismiss = viewModel::dismissError,
                )

                if (state.errorDetail != null) {
                    TextButton(
                        onClick = { detailsShown = !detailsShown },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = if (detailsShown) "Скрыть подробности" else "Подробности",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (detailsShown) {
                        Text(
                            text = state.errorDetail.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        )
                    }
                }

                if (layoutSuspect) {
                    InputHint(
                        text = "Похоже, часть символов набрана в русской раскладке — на вид они " +
                            "не отличаются от латинских.",
                        action = "Исправить раскладку",
                        onClick = {
                            viewModel.onUsernameChange(LoginHints.toLatin(state.username))
                            password = LoginHints.toLatin(password)
                        },
                    )
                }

                if (spaceSuspect) {
                    InputHint(
                        text = "В начале или в конце есть пробел — его легко не заметить.",
                        action = "Убрать пробелы",
                        onClick = {
                            viewModel.onUsernameChange(state.username.trim())
                            password = password.trim()
                        },
                    )
                }

                if (state.error != null && state.siblingCities.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "У этого города несколько филиалов. Возможно, ваш — другой:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    state.siblingCities.forEach { sibling ->
                        Text(
                            text = sibling.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { viewModel.onCityChange(sibling.id) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = submit,
                    enabled = !state.submitting,
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Входим…")
                    } else {
                        Text("Войти", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Логин и пароль — те же, что на journal.top-academy.ru. " +
                    "Данные хранятся только на этом устройстве.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(32.dp))
        }

        if (cityMenuOpen) {
            CityPickerDialog(
                cities = state.cities,
                query = cityQuery,
                onQueryChange = { cityQuery = it },
                onPick = {
                    viewModel.onCityChange(it.id)
                    cityMenuOpen = false
                },
                onDismiss = { cityMenuOpen = false },
            )
        }
    }
}

private val FieldShape = RoundedCornerShape(14.dp)

@Composable
private fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)

// Белый логотип на красном градиенте. Беру монохромную иконку,
// цветная на красном фоне не видна.
@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.extra.heroStart, MaterialTheme.extra.heroEnd),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(88.dp),
        )
    }
}

@Composable
private fun InputHint(text: String, action: String, onClick: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.extra.warningContainer)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CityPickerDialog(
    cities: List<CityDto>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (CityDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered = remember(cities, query) {
        if (query.isBlank()) cities
        else cities.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 18.dp),
        ) {
            Text(
                text = "Выберите филиал",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Поиск по городу") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = FieldShape,
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                items(filtered, key = { it.id }) { city ->
                    Text(
                        text = city.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(city) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                    RowDivider(inset = 20)
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
}
