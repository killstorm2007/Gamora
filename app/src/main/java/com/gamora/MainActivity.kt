package com.gamora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Movimiento(
    val descripcion: String,
    val categoria: String,
    val cantidad: Double,
    val esIngreso: Boolean
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    GamoraApp()
                }
            }
        }
    }
}

@Composable
fun GamoraApp() {

    var movimientos by remember {
        mutableStateOf(
            listOf(
                Movimiento(
                    descripcion = "Salario",
                    categoria = "Trabajo",
                    cantidad = 8000.0,
                    esIngreso = true
                ),
                Movimiento(
                    descripcion = "Comida",
                    categoria = "Alimentación",
                    cantidad = 250.0,
                    esIngreso = false
                ),
                Movimiento(
                    descripcion = "Transporte",
                    categoria = "Transporte",
                    cantidad = 120.0,
                    esIngreso = false
                ),
                Movimiento(
                    descripcion = "Netflix",
                    categoria = "Entretenimiento",
                    cantidad = 180.0,
                    esIngreso = false
                ),
                Movimiento(
                    descripcion = "Supermercado",
                    categoria = "Alimentación",
                    cantidad = 650.0,
                    esIngreso = false
                )
            )
        )
    }

    var mostrarDialogo by remember {
        mutableStateOf(false)
    }

    val ingresos = movimientos
        .filter { it.esIngreso }
        .sumOf { it.cantidad }

    val gastos = movimientos
        .filter { !it.esIngreso }
        .sumOf { it.cantidad }

    val saldo = ingresos - gastos

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {

        Text(
            text = "GAMORA",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Tus finanzas bajo control",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        SaldoCard(saldo)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            ResumenCard(
                titulo = "Ingresos",
                cantidad = ingresos,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            ResumenCard(
                titulo = "Gastos",
                cantidad = gastos,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "Movimientos",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = {
                    mostrarDialogo = true
                }
            ) {
                Text("+ Agregar")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            items(movimientos) { movimiento ->

                MovimientoCard(movimiento)
            }
        }
    }

    if (mostrarDialogo) {

        AgregarMovimientoDialog(
            onCerrar = {
                mostrarDialogo = false
            },
            onAgregar = { movimiento ->

                movimientos = movimientos + movimiento

                mostrarDialogo = false
            }
        )
    }
}

@Composable
fun SaldoCard(saldo: Double) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                text = "Saldo disponible",
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$${"%.2f".format(saldo)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ResumenCard(
    titulo: String,
    cantidad: Double,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = titulo,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$${"%.2f".format(cantidad)}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MovimientoCard(movimiento: Movimiento) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = movimiento.descripcion,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = movimiento.categoria,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = if (movimiento.esIngreso) {
                    "+$${"%.2f".format(movimiento.cantidad)}"
                } else {
                    "-$${"%.2f".format(movimiento.cantidad)}"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AgregarMovimientoDialog(
    onCerrar: () -> Unit,
    onAgregar: (Movimiento) -> Unit
) {

    var descripcion by remember {
        mutableStateOf("")
    }

    var categoria by remember {
        mutableStateOf("")
    }

    var cantidadTexto by remember {
        mutableStateOf("")
    }

    var esIngreso by remember {
        mutableStateOf(true)
    }

    AlertDialog(

        onDismissRequest = onCerrar,

        title = {
            Text("Agregar movimiento")
        },

        text = {

            Column {

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = {
                        descripcion = it
                    },
                    label = {
                        Text("Descripción")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = categoria,
                    onValueChange = {
                        categoria = it
                    },
                    label = {
                        Text("Categoría")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = cantidadTexto,
                    onValueChange = {
                        cantidadTexto = it
                    },
                    label = {
                        Text("Cantidad")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    if (esIngreso) {

                        Button(
                            onClick = {
                                esIngreso = true
                            }
                        ) {
                            Text("Ingreso")
                        }

                        OutlinedButton(
                            onClick = {
                                esIngreso = false
                            }
                        ) {
                            Text("Gasto")
                        }

                    } else {

                        OutlinedButton(
                            onClick = {
                                esIngreso = true
                            }
                        ) {
                            Text("Ingreso")
                        }

                        Button(
                            onClick = {
                                esIngreso = false
                            }
                        ) {
                            Text("Gasto")
                        }
                    }
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick = {

                    val cantidad = cantidadTexto.toDoubleOrNull()

                    if (
                        descripcion.isNotBlank() &&
                        categoria.isNotBlank() &&
                        cantidad != null &&
                        cantidad > 0
                    ) {

                        onAgregar(
                            Movimiento(
                                descripcion = descripcion,
                                categoria = categoria,
                                cantidad = cantidad,
                                esIngreso = esIngreso
                            )
                        )
                    }
                }
            ) {
                Text("Agregar")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onCerrar
            ) {
                Text("Cancelar")
            }
        }
    )
}