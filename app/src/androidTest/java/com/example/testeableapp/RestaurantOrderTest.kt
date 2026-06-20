package com.example.testeableapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.testeableapp.model.MenuData
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RestaurantOrderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Cada test de UI usa la MISMA Activity (y el mismo ViewModel) durante
     * toda la clase, porque createAndroidComposeRule no la relanza por test.
     * Si un test anterior dejó items en el pedido, el siguiente test
     * arrancaría con estado "sucio" y fallaría sin tener un bug real.
     * Por eso, antes de cada test, si el pedido NO está vacío, se vacía
     * simulando que el usuario decrementa todos los items hasta 0.
     */
    @Before
    fun limpiarPedidoAntesDeCadaTest() {
        composeTestRule.waitForIdle()

        val emptyMessageExists = composeTestRule
            .onAllNodesWithTag("emptyOrderMessage")
            .fetchSemanticsNodes()
            .isNotEmpty()

        if (!emptyMessageExists) {
            // Hay items en el pedido: los decrementamos todos hasta vaciarlo.
            MenuData.items.forEach { item ->
                var stillThere = composeTestRule
                    .onAllNodesWithTag("orderItem_${item.id}")
                    .fetchSemanticsNodes()
                    .isNotEmpty()

                while (stillThere) {
                    composeTestRule.onNodeWithTag("decrementOrderItem_${item.id}")
                        .performScrollTo()
                        .performClick()
                    composeTestRule.waitForIdle()
                    stillThere = composeTestRule
                        .onAllNodesWithTag("orderItem_${item.id}")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            }
        }


        val confirmationOpen = composeTestRule
            .onAllNodesWithTag("confirmationDialog")
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (confirmationOpen) {
            composeTestRule.onNodeWithTag("confirmationOkButton").performClick()
            composeTestRule.waitForIdle()
        }
    }


    // 1. Mensaje de pedido vacío visible al inicio
    @Test
    fun mensajeDePedidoVacio_esVisibleAlInicio() {
        composeTestRule.onNodeWithTag("emptyOrderMessage")
            .performScrollTo()
            .assertIsDisplayed()
    }


    // 2. Todos los items del menú son visibles
    // La pantalla usa verticalScroll en MainActivity.kt, por lo que cada
    // item debe llevarse a la vista con performScrollTo() antes de
    // afirmar que está mostrado en pantalla.
    @Test
    fun todosLosItemsDelMenu_sonVisibles() {
        MenuData.items.forEach { item ->
            composeTestRule.onNodeWithTag("menuItem_${item.id}")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("menuItemName_${item.id}")
                .assertTextEquals(item.name)
        }
    }


    // 3. El total general se actualiza
    @Test
    fun elTotalGeneral_seActualizaAlAgregarItems() {
        val patatas = MenuData.items.first { it.id == 1 } // 5.50

        composeTestRule.onNodeWithTag("addButton_${patatas.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("totalValue")
            .performScrollTo()
            .assertTextEquals("%.2f €".format(patatas.price))

        composeTestRule.onNodeWithTag("incrementOrderItem_${patatas.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("totalValue")
            .performScrollTo()
            .assertTextEquals("%.2f €".format(patatas.price * 2))
    }


    // PRUEBAS DE UI ADICIONALES (análisis propio) - 2 pts
    /**
     * ADICIONAL #1: El subtotal de UNA línea específica del pedido
     * (orderItemSubtotal_{id}) se recalcula correctamente al incrementar
     * SOLO la cantidad de ese ítem, sin verse afectado por otro ítem
     * presente en el carrito.
     *
     * JUSTIFICACIÓN: El test obligatorio de "total general" valida la
     * suma global, pero la UI también muestra un subtotal por fila
     * (cantidad x precio) en cada OrderItemRow. Es un cálculo independiente
     * en el código (variable local "subtotal" dentro de OrderItemRow) que
     * podría desincronizarse del total general si hubiera un error de
     * binding o de actualización parcial de la lista. Probarlo junto con
     * un segundo ítem en el carrito asegura que cada fila refleja SU
     * propia cantidad y no se confunde con la de otro ítem.
     */
    @Test
    fun subtotalDeUnaLinea_seActualizaIndependientementeDeOtroItem() {
        val patatas = MenuData.items.first { it.id == 1 } // 5.50
        val agua = MenuData.items.first { it.id == 5 }    // 1.50

        composeTestRule.onNodeWithTag("addButton_${patatas.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("addButton_${agua.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // Subtotal inicial de patatas: 1 x 5.50
        composeTestRule.onNodeWithTag("orderItemSubtotal_${patatas.id}")
            .performScrollTo()
            .assertTextEquals("%.2f €".format(patatas.price))

        // Incrementamos SOLO patatas, dos veces más (cantidad = 3)
        composeTestRule.onNodeWithTag("incrementOrderItem_${patatas.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("incrementOrderItem_${patatas.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // El subtotal de patatas debe reflejar 3 x 5.50, sin tocar el de agua
        composeTestRule.onNodeWithTag("orderItemSubtotal_${patatas.id}")
            .performScrollTo()
            .assertTextEquals("%.2f €".format(patatas.price * 3))

        composeTestRule.onNodeWithTag("orderItemSubtotal_${agua.id}")
            .performScrollTo()
            .assertTextEquals("%.2f €".format(agua.price * 1))
    }

    /**
     * ADICIONAL #2: Dos items distintos agregados al pedido mantienen
     * cantidades independientes entre sí en la sección "Tu Pedido".
     *
     * JUSTIFICACIÓN: Las cantidades se guardan en un Map<Int, Int> indexado
     * por itemId. Un error de indexación (por ejemplo, usar siempre la
     * última key modificada, o sobrescribir en vez de agregar una nueva
     * entrada) podría hacer que agregar un segundo producto reemplace o
     * altere la cantidad del primero sin que ningún test obligatorio lo
     * detecte, ya que estos solo manipulan un único ítem a la vez. Esta
     * prueba agrega dos productos con cantidades distintas y verifica que
     * ambas etiquetas de cantidad (orderItemQuantity_{id}) muestren el
     * valor correcto de forma simultánea.
     */
    @Test
    fun dosItemsDistintos_mantienenCantidadesIndependientesEnElPedido() {
        val croquetas = MenuData.items.first { it.id == 2 } // Croquetas
        val refresco = MenuData.items.first { it.id == 6 }  // Refresco

        composeTestRule.onNodeWithTag("addButton_${croquetas.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("incrementOrderItem_${croquetas.id}")
            .performScrollTo()
            .performClick() // croquetas x2

        composeTestRule.onNodeWithTag("addButton_${refresco.id}")
            .performScrollTo()
            .performClick() // refresco x1
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("orderItemQuantity_${croquetas.id}")
            .performScrollTo()
            .assertTextEquals("2")

        composeTestRule.onNodeWithTag("orderItemQuantity_${refresco.id}")
            .performScrollTo()
            .assertTextEquals("1")
    }
}