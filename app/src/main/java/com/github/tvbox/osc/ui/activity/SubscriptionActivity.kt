package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.text.TextUtils
import android.view.View
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.Source
import com.github.tvbox.osc.bean.Subscription
import com.github.tvbox.osc.databinding.ActivitySubscriptionBinding
import com.github.tvbox.osc.ui.adapter.SubscriptionAdapter
import com.github.tvbox.osc.ui.dialog.ChooseSourceDialog
import com.github.tvbox.osc.ui.dialog.SubsTipDialog
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog.OnSubsciptionListener
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.Utils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.lxj.xpopup.XPopup
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.obsez.android.lib.filechooser.ChooserDialog
import com.orhanobut.hawk.Hawk
import java.util.function.Consumer

class SubscriptionActivity : BaseVbActivity<ActivitySubscriptionBinding>() {

    companion object {
        const val EXTRA_LIVE = "live_subscription"
    }

    private var isLiveSubscription = false
    private var mBeforeUrl = ""
    private var mSelectedUrl = ""
    private var mSubscriptions: MutableList<Subscription> = ArrayList()
    private var mSubscriptionAdapter = SubscriptionAdapter()
    private val mSources: MutableList<Source> = ArrayList()

    override fun init() {
        isLiveSubscription = intent?.getBooleanExtra(EXTRA_LIVE, false) ?: false
        mBeforeUrl = Hawk.get(if (isLiveSubscription) HawkConfig.LIVE_URL else HawkConfig.API_URL, "")
        mSubscriptions = Hawk.get<MutableList<Subscription>>(
            if (isLiveSubscription) HawkConfig.LIVE_SUBSCRIPTIONS else HawkConfig.SUBSCRIPTIONS,
            ArrayList()
        )
        mBinding.titleBar.setTitle(if (isLiveSubscription) "直播订阅" else "订阅管理")
        mBinding.rv.setAdapter(mSubscriptionAdapter)
        mSubscriptions.forEach(Consumer { item: Subscription ->
            if (item.isChecked) {
                mSelectedUrl = item.url
            }
        })

        mSubscriptionAdapter.setNewData(mSubscriptions)
        mBinding.ivUseTip.setOnClickListener {
            XPopup.Builder(this)
                .asCustom(SubsTipDialog(this))
                .show()
        }

        mBinding.titleBar.rightView.setOnClickListener {//添加订阅
            XPopup.Builder(this)
                .autoFocusEditText(false)
                .asCustom(
                    SubsciptionDialog(
                        this,
                        "订阅: " + (mSubscriptions.size + 1),
                        object : OnSubsciptionListener {
                            override fun onConfirm(
                                name: String,
                                url: String,
                                checked: Boolean
                            ) { //只有addSub2List用到,看注释,单线路才生效,其余方法仅作为参数继续传递
                                for (item in mSubscriptions) {
                                    if (item.url == url) {
                                        ToastUtils.showLong("订阅地址与" + item.name + "相同")
                                        return
                                    }
                                }
                                addSubscription(name, url, checked)
                            }

                            override fun chooseLocal(checked: Boolean) { //本地导入
                                if (!XXPermissions.isGranted(
                                        mContext,
                                        Permission.MANAGE_EXTERNAL_STORAGE
                                    )
                                ) {
                                    showPermissionTipPopup(checked)
                                } else {
                                    pickFile(checked)
                                }
                            }
                        })
                ).show()
        }

        mSubscriptionAdapter.setOnItemChildClickListener { _: BaseQuickAdapter<*, *>?, view: View, position: Int ->
            LogUtils.d("删除订阅")
            if (view.id == R.id.iv_del) {
                if (mSubscriptions.get(position).isChecked) {
                    ToastUtils.showShort("不能删除当前使用的订阅")
                    return@setOnItemChildClickListener
                }
                XPopup.Builder(this@SubscriptionActivity)
                    .asConfirm("删除订阅", "确定删除订阅吗？") {
                        mSubscriptions.removeAt(position)
                        //删除/选择只刷新,不触发重新排序
                        mSubscriptionAdapter.notifyDataSetChanged()
                    }.show()
            }
        }

        mSubscriptionAdapter.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->  //选择订阅
            for (i in mSubscriptions.indices) {
                val subscription = mSubscriptions[i]
                if (i == position) {
                    subscription.setChecked(true)
                    mSelectedUrl = subscription.url
                } else {
                    subscription.setChecked(false)
                }
            }
            //删除/选择只刷新,不触发重新排序
            mSubscriptionAdapter.notifyDataSetChanged()
        }

        mSubscriptionAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { adapter: BaseQuickAdapter<*, *>?, view: View, position: Int ->
                val item = mSubscriptions[position]
                XPopup.Builder(this)
                    .atView(view.findViewById(R.id.tv_name))
                    .hasShadowBg(false)
                    .asAttachList(
                        arrayOf(
                            if (item.isTop) "取消置顶" else "置顶",
                            "重命名",
                            "复制地址"
                        ), null
                    ) { index: Int, _: String? ->
                        when (index) {
                            0 -> {
                                item.isTop = !item.isTop
                                mSubscriptions[position] = item
                                mSubscriptionAdapter.setNewData(mSubscriptions)
                            }
                            1 -> {
                                XPopup.Builder(this)
                                    .asInputConfirm(
                                        "更改为",
                                        "",
                                        item.name,
                                        "新的订阅名",
                                        { text ->
                                            if (!TextUtils.isEmpty(text)) {
                                                if (text.trim { it <= ' ' }.length > 8) {
                                                    ToastUtils.showShort("不要过长,不方便记忆")
                                                } else {
                                                    item.name = text.trim { it <= ' ' }
                                                    mSubscriptionAdapter.notifyItemChanged(position)
                                                }
                                            }
                                        },
                                        null,
                                        R.layout.dialog_input
                                    ).show()
                            }
                            2 -> {
                                ClipboardUtils.copyText(mSubscriptions.get(position).url)
                                ToastUtils.showLong("已复制")
                            }
                        }
                    }.show()
                true
            }
    }

    private fun showPermissionTipPopup(checked: Boolean) {
        XPopup.Builder(this@SubscriptionActivity)
            .isDarkTheme(Utils.isDarkTheme())
            .asConfirm("提示", "这将访问您设备文件的读取权限") {
                XXPermissions.with(this)
                    .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) {
                                pickFile(checked)
                            } else {
                                ToastUtils.showLong("部分权限未正常授予,请授权")
                            }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                ToastUtils.showLong("读写文件权限被永久拒绝，请手动授权")
                                // 如果是被永久拒绝就跳转到应用权限系统设置页面
                                XXPermissions.startPermissionActivity(
                                    this@SubscriptionActivity,
                                    permissions
                                )
                            } else {
                                ToastUtils.showShort("获取权限失败")
                                showPermissionTipPopup(checked)
                            }
                        }
                    })
            }.show()
    }

    /**
     *
     * @param checked 与showPermissionTipPopup一样,只记录并传递选中状态
     */
    private fun pickFile(checked: Boolean) {
        ChooserDialog(this@SubscriptionActivity, R.style.FileChooser)
            .withFilter(false, false, "txt", "json")
            .withStartFile(
                if (TextUtils.isEmpty(Hawk.get("before_selected_path"))) "/storage/emulated/0/Download" else Hawk.get(
                    "before_selected_path"
                )
            )
            .withChosenListener(ChooserDialog.Result { _, pathFile ->
                Hawk.put("before_selected_path", pathFile.parent)
                val clanPath =
                    pathFile.absolutePath.replace("/storage/emulated/0", "clan://localhost")
                for (item in mSubscriptions) {
                    if (item.url == clanPath) {
                        ToastUtils.showLong("订阅地址与" + item.name + "相同")
                        return@Result
                    }
                }
                addSubscription(pathFile.name, clanPath, checked)
            })
            .build()
            .show()
    }

    private fun addSubscription(name: String, url: String, checked: Boolean) {
        if (url.startsWith("clan://")) {
            addSub2List(name, url, checked)
            mSubscriptionAdapter.setNewData(mSubscriptions)
        } else if (url.startsWith("http")) {
            showLoadingDialog()
            OkGo.get<String>(url)
                .tag("get_subscription")
                .execute(object : AbsCallback<String?>() {
                    override fun onSuccess(response: Response<String?>) {
                        dismissLoadingDialog()
                        try {
                            val bodyText = response.body().orEmpty().trim()
                            val json = JsonParser.parseString(bodyText).asJsonObject
                            val urlsValue = json.get("urls")
                            val urls = if (urlsValue != null && urlsValue.isJsonArray) urlsValue.asJsonArray else null
                            val storeHouse = findRepoArray(json)

                            if (urls != null && urls.size() > 0) {
                                val entries = parseSourceEntries(urls, arrayOf("name", "title", "sourceName"), arrayOf("url", "api", "sourceUrl"))
                                if (entries.isNotEmpty()) {
                                    if (checked) ToastUtils.showLong("已展开多条线路")
                                    entries.forEachIndexed { index, entry ->
                                        addSub2List(entry.first, entry.second, checked && index == 0)
                                    }
                                } else {
                                    addSub2List(name, url, checked)
                                }
                            } else if (storeHouse != null && storeHouse.size() > 0) {
                                val sources = parseSourceEntries(storeHouse, arrayOf("sourceName", "name", "title"), arrayOf("sourceUrl", "url", "api"))
                                if (sources.isEmpty()) {
                                    addSub2List(name, url, checked)
                                } else {
                                    mSources.clear()
                                    sources.forEach { mSources.add(Source(it.first, it.second)) }
                                    if (mSources.size == 1) {
                                        addSubscription(mSources[0].sourceName, mSources[0].sourceUrl, checked)
                                    } else {
                                        XPopup.Builder(this@SubscriptionActivity)
                                            .asCustom(ChooseSourceDialog(this@SubscriptionActivity, mSources) { position: Int, _: String? ->
                                                addSubscription(mSources[position].sourceName, mSources[position].sourceUrl, checked)
                                            })
                                            .show()
                                    }
                                }
                            } else {
                                addSub2List(name, url, checked)
                            }
                        } catch (th: Throwable) {
                            addSub2List(name, url, checked)
                        }
                        mSubscriptionAdapter.setNewData(mSubscriptions)
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        return response.body()!!.string()
                    }

                    override fun onError(response: Response<String?>) {
                        super.onError(response)
                        dismissLoadingDialog()
                        ToastUtils.showLong("订阅失败,请检查地址或网络状态")
                    }
                })
        } else {
            ToastUtils.showShort("订阅格式不正确")
        }
    }

    /**
     * 仅当选中本地文件和添加的为单线路时,使用此订阅生效。多线路会直接解析全部并添加,多仓会展开并选择,最后也按多线路处理,直接添加
     * @param name
     * @param url
     * @param checkNewest
     */
    private fun findRepoArray(json: JsonObject): JsonArray? {
        for (key in arrayOf("storeHouse", "storehouse", "storeHouses", "repositories", "repos", "sources")) {
            val value = json.get(key)
            if (value != null && value.isJsonArray) return value.asJsonArray
        }
        return null
    }

    private fun parseSourceEntries(array: JsonArray, nameKeys: Array<String>, urlKeys: Array<String>): MutableList<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (element in array) {
            var entryName = ""
            var entryUrl = ""
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                for (key in nameKeys) {
                    val value = obj.get(key)
                    if (value != null && value.isJsonPrimitive) {
                        entryName = value.asString.trim()
                        if (entryName.isNotEmpty()) break
                    }
                }
                for (key in urlKeys) {
                    val value = obj.get(key)
                    if (value != null && value.isJsonPrimitive) {
                        entryUrl = value.asString.trim()
                        if (entryUrl.isNotEmpty()) break
                    }
                }
            } else if (element.isJsonPrimitive) {
                entryUrl = element.asString.trim()
            }
            if (entryUrl.isNotEmpty()) {
                if (entryName.isEmpty()) entryName = "订阅 " + (result.size + 1)
                if (result.none { it.second == entryUrl }) result.add(entryName to entryUrl)
            }
        }
        return result
    }

    private fun addSub2List(name: String, url: String, checkNewest: Boolean) {
        if (checkNewest) { //选中最新的,清除以前的选中订阅
            for (subscription in mSubscriptions) {
                if (subscription.isChecked) {
                    subscription.setChecked(false)
                }
            }
            mSelectedUrl = url
            mSubscriptions.add(Subscription(name, url).setChecked(true))
        } else {
            mSubscriptions.add(Subscription(name, url).setChecked(false))
        }
    }

    override fun onPause() {
        super.onPause()
        if (isLiveSubscription) {
            Hawk.put(HawkConfig.LIVE_URL, mSelectedUrl)
            Hawk.put<List<Subscription>?>(HawkConfig.LIVE_SUBSCRIPTIONS, mSubscriptions)
        } else {
            Hawk.put(HawkConfig.API_URL, mSelectedUrl)
            Hawk.put<List<Subscription>?>(HawkConfig.SUBSCRIPTIONS, mSubscriptions)
        }
    }

    override fun finish() {
        if (!isLiveSubscription && !TextUtils.isEmpty(mSelectedUrl) && mBeforeUrl != mSelectedUrl) {
            val intent = Intent(this, MainActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        OkGo.getInstance().cancelTag("get_subscription")
    }
}