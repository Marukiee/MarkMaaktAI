package nl.markmaaktmedia.markmaaktai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import kotlin.math.abs

data class PillNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int = 0,
)

/**
 * The floating navigation bar.
 *
 * One indicator, not four backgrounds. A single pill lives behind the row and travels
 * to whichever tab was tapped, which is the thing that makes the bar read as one
 * object rather than four buttons that light up independently.
 *
 * The elastic part comes from the travel itself: the pill stretches along its
 * direction of movement in proportion to how far it still has to go, and settles back
 * to its resting width as it arrives. So a hop to the neighbouring tab barely deforms
 * and a jump across the bar visibly stretches, which is what makes the distance
 * legible rather than decorative. The stretch is applied as a scale on the pill only,
 * so the labels never distort with it.
 */
@Composable
fun PillNavigationBar(
    items: List<PillNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (items.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .padding(Padding),
    ) {
        val slotWidth = (maxWidth - Padding * 2) / items.size
        val target = slotWidth * selectedIndex

        val position = remember { Animatable(target.value) }
        LaunchedEffect(target, slotWidth) {
            position.animateTo(target.value, animationSpec = MarkMotion.springy())
        }

        // Distance still to travel, normalised against one slot. Feeds the stretch.
        val remaining = abs(target.value - position.value) / slotWidth.value.coerceAtLeast(1f)
        val stretch = 1f + (remaining.coerceIn(0f, 1.6f) * StretchFactor)
        val squash = 1f - (remaining.coerceIn(0f, 1.6f) * SquashFactor)

        Box(
            modifier = Modifier
                .offset(x = position.value.dp)
                .width(slotWidth)
                .height(IndicatorHeight)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    scaleX = stretch
                    scaleY = squash
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(indicatorColor)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                PillNavTab(
                    item = item,
                    selected = index == selectedIndex,
                    onSelect = { onSelect(index) },
                    modifier = Modifier.width(slotWidth),
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PillNavTab(
    item: PillNavItem,
    selected: Boolean,
    onSelect: () -> Unit,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedContentColor else unselectedContentColor,
        animationSpec = MarkMotion.colourSpec(),
        label = "navTabColour",
    )
    // The selected tab rides a touch higher and larger, so the pill has something
    // to be holding up rather than sitting behind.
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MarkMotion.springy(),
        label = "navTabLift",
    )

    Column(
        modifier = modifier
            .height(IndicatorHeight)
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .graphicsLayer {
                scaleX = 1f + lift * 0.06f
                scaleY = 1f + lift * 0.06f
                translationY = -lift * 1.5f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(IconSize),
            )
            if (item.badgeCount > 0) {
                CountBadge(
                    count = item.badgeCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-6).dp),
                )
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val BarHeight: Dp = 64.dp
private val IndicatorHeight: Dp = 52.dp
private val Padding: Dp = 6.dp
private val IconSize: Dp = 22.dp

/** How much of a full slot of remaining travel turns into stretch, and into squash. */
private const val StretchFactor = 0.14f
private const val SquashFactor = 0.05f
