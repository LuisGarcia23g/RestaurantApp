package com.example.testeableapp

import com.example.testeableapp.model.MenuData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Pruebas unitarias
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RestaurantViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RestaurantViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }


    // 1. Agregar item al pedido
    @Test
    fun agregarItem_seAgregaCorrectamenteAlPedido() = runTest(testDispatcher) {
        val item = MenuData.items.first() // Patatas Bravas, id = 1

        viewModel.addItem(item.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.quantities.value[item.id])
        assertTrue(viewModel.orderedItems.value.contains(item))
        assertFalse(viewModel.isEmpty.value)
    }


    // 2. Incrementar/Decrementar cantidad
    @Test
    fun incrementarYDecrementarCantidad_actualizaCorrectamente() = runTest(testDispatcher) {
        val item = MenuData.items.first()

        viewModel.addItem(item.id)          // cantidad = 1
        viewModel.incrementItem(item.id)    // cantidad = 2
        viewModel.incrementItem(item.id)    // cantidad = 3
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.quantities.value[item.id])

        viewModel.decrementItem(item.id)    // cantidad = 2
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.quantities.value[item.id])
    }


    // 3. Eliminar item al decrementar desde 1
    @Test
    fun decrementarDesdeUno_eliminaElItemDelPedido() = runTest(testDispatcher) {
        val item = MenuData.items.first()

        viewModel.addItem(item.id)          // cantidad = 1
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.quantities.value[item.id])

        viewModel.decrementItem(item.id)    // debería eliminarlo JAJA
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.quantities.value[item.id])
        assertFalse(viewModel.orderedItems.value.contains(item))
        assertTrue(viewModel.isEmpty.value)
    }


    // 4. Cálculo del total a pagar
    @Test
    fun calculoDelTotal_seValidaLaFormulaUsada() = runTest(testDispatcher) {
        val patatas = MenuData.items.first { it.id == 1 } // 5.50
        val agua = MenuData.items.first { it.id == 5 }    // 1.50

        viewModel.addItem(patatas.id)       // 1 x 5.50
        viewModel.addItem(agua.id)          // 1 x 1.50
        viewModel.incrementItem(agua.id)    // 2 x 1.50
        testDispatcher.scheduler.advanceUntilIdle()

        val totalEsperado = (1 * patatas.price) + (2 * agua.price) // 5.50 + 3.00 = 8.50
        assertEquals(totalEsperado, viewModel.total.value, 0.001)
    }


    // 5. ADICIONAL #1
    @Test
    fun decrementarItemInexistente_noLanzaExcepcionYNoModificaEstado() = runTest(testDispatcher) {
        val estadoInicial = viewModel.quantities.value

        viewModel.decrementItem(999)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(estadoInicial, viewModel.quantities.value)
        assertTrue(viewModel.isEmpty.value)
    }


    // 6. ADICIONAL #2
    @Test
    fun calculoDelTotal_conItemsDeVariasCategorias_yAlEliminarUno() = runTest(testDispatcher) {
        val tapa = MenuData.items.first { it.category == "Tapas" }
        val bebida = MenuData.items.first { it.category == "Bebidas" }
        val postre = MenuData.items.first { it.category == "Postres" }

        viewModel.addItem(tapa.id)
        viewModel.addItem(bebida.id)
        viewModel.addItem(postre.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val totalConTres = tapa.price + bebida.price + postre.price
        assertEquals(totalConTres, viewModel.total.value, 0.001)

        viewModel.decrementItem(bebida.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val totalSinBebida = tapa.price + postre.price
        assertEquals(totalSinBebida, viewModel.total.value, 0.001)
        assertFalse(viewModel.orderedItems.value.contains(bebida))
    }
}