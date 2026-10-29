package cn.baymaxawa.xmsfnotichannel.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.baymaxawa.xmsfnotichannel.R
import cn.baymaxawa.xmsfnotichannel.ui.model.AppUi
import cn.baymaxawa.xmsfnotichannel.ui.model.ChannelUi
import cn.baymaxawa.xmsfnotichannel.ui.util.BlurredBar
import cn.baymaxawa.xmsfnotichannel.ui.util.blurSource
import cn.baymaxawa.xmsfnotichannel.ui.util.pageScrollModifiers
import cn.baymaxawa.xmsfnotichannel.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单个应用的通知通道详情页。
 *
 * 顶部是总开关（一次改这个应用下全部通道），下面是逐条通道的开关。
 */
@Composable
fun ChannelDetailPageView(
    app: AppUi,
    isBlurEnabled: Boolean = true,
    /** 底部悬浮导航栏占用的高度，避免最后一条通道被底栏遮住 */
    hitBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onBack: () -> Unit = {},
    onToggleApp: (AppUi, Boolean) -> Unit = { _, _ -> },
    onToggleChannel: (ChannelUi, Boolean) -> Unit = { _, _ -> },
) {
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) {
        androidx.compose.ui.graphics.Color.Transparent
    } else {
        MiuixTheme.colorScheme.surface
    }

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = app.appName,
                    subtitle = stringResource(
                        R.string.label_app_enabled_summary,
                        app.enabledCount,
                        app.totalCount,
                    ),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = scrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp + hitBottomPadding,
                ),
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                MiuixText(
                                    text = stringResource(R.string.label_toggle_all_for_app),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                                )
                                Spacer(Modifier.height(2.dp))
                                MiuixText(
                                    text = stringResource(
                                        R.string.label_app_enabled_summary,
                                        app.enabledCount,
                                        app.totalCount,
                                    ),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                )
                            }
                            Switch(
                                checked = app.allEnabled,
                                onCheckedChange = { onToggleApp(app, it) },
                            )
                        }
                    }
                }

                item { SmallTitle(text = stringResource(R.string.section_channels)) }

                items(
                    items = app.channels,
                    key = { it.channelId + "|" + it.displayName },
                ) { channel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixText(
                                text = channel.displayName,
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = channel.enabled,
                                onCheckedChange = { onToggleChannel(channel, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}