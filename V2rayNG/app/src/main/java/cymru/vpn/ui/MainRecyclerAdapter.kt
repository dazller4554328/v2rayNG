package cymru.vpn.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import cymru.vpn.AppConfig
import cymru.vpn.R
import cymru.vpn.contracts.MainAdapterListener
import cymru.vpn.databinding.ItemRecyclerCountryBinding
import cymru.vpn.databinding.ItemRecyclerFooterBinding
import cymru.vpn.databinding.ItemRecyclerMainBinding
import cymru.vpn.dto.ProfileItem
import cymru.vpn.dto.ServersCache
import cymru.vpn.extension.nullIfBlank
import cymru.vpn.handler.AngConfigManager
import cymru.vpn.handler.MmkvManager
import cymru.vpn.helper.ItemTouchHelperAdapter
import cymru.vpn.helper.ItemTouchHelperViewHolder
import cymru.vpn.util.CountryUtil
import cymru.vpn.viewmodel.MainViewModel
import java.util.Collections

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
        private const val VIEW_TYPE_COUNTRY = 3
        private const val UNKNOWN_COUNTRY = "ZZ"
    }

    private val doubleColumnDisplay = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)

    // Raw server data grouped by country
    private var allServers: MutableList<ServersCache> = mutableListOf()
    private var countryGroups: MutableMap<String, MutableList<ServersCache>> = mutableMapOf()
    private val expandedCountries: MutableSet<String> = mutableSetOf()

    // Flat display list mixing country headers and server items
    private val displayItems: MutableList<DisplayItem> = mutableListOf()

    sealed class DisplayItem {
        data class CountryHeader(
            val countryCode: String,
            val countryName: String,
            val flag: String,
            val serverCount: Int,
            val isExpanded: Boolean
        ) : DisplayItem()

        data class ServerEntry(
            val serversCache: ServersCache
        ) : DisplayItem()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        allServers = newData?.toMutableList() ?: mutableListOf()

        if (position >= 0) {
            // Single item update (e.g. test result) — find it in the display list
            val guid = newData?.getOrNull(position)?.guid
            if (guid != null) {
                val displayPos = displayItems.indexOfFirst {
                    it is DisplayItem.ServerEntry && it.serversCache.guid == guid
                }
                if (displayPos >= 0) {
                    // Update the cached server data
                    val entry = displayItems[displayPos] as DisplayItem.ServerEntry
                    val updatedServer = newData.firstOrNull { it.guid == guid }
                    if (updatedServer != null) {
                        displayItems[displayPos] = DisplayItem.ServerEntry(updatedServer)
                    }
                    notifyItemChanged(displayPos)
                    return
                }
            }
        }

        rebuildGroups()
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    private fun rebuildGroups() {
        countryGroups.clear()

        for (server in allServers) {
            val code = CountryUtil.extractCountryCode(server.profile.remarks) ?: UNKNOWN_COUNTRY
            countryGroups.getOrPut(code) { mutableListOf() }.add(server)
        }
    }

    private fun rebuildDisplayList() {
        displayItems.clear()

        if (countryGroups.size <= 1 && countryGroups.keys.firstOrNull() == UNKNOWN_COUNTRY) {
            // No country codes detected — fall back to flat list
            for (server in allServers) {
                displayItems.add(DisplayItem.ServerEntry(server))
            }
            return
        }

        // Sort countries: known countries alphabetically by name, unknown last
        val sortedCodes = countryGroups.keys.sortedWith(compareBy<String> {
            if (it == UNKNOWN_COUNTRY) 1 else 0
        }.thenBy {
            CountryUtil.getCountryName(it)
        })

        for (code in sortedCodes) {
            val servers = countryGroups[code] ?: continue
            val isExpanded = code in expandedCountries
            val name = if (code == UNKNOWN_COUNTRY) "Other" else CountryUtil.getCountryName(code)
            val flag = if (code == UNKNOWN_COUNTRY) "" else CountryUtil.countryCodeToFlag(code)

            displayItems.add(
                DisplayItem.CountryHeader(
                    countryCode = code,
                    countryName = name,
                    flag = flag,
                    serverCount = servers.size,
                    isExpanded = isExpanded
                )
            )

            if (isExpanded) {
                for (server in servers) {
                    displayItems.add(DisplayItem.ServerEntry(server))
                }
            }
        }
    }

    private fun toggleCountry(countryCode: String) {
        if (countryCode in expandedCountries) {
            expandedCountries.remove(countryCode)
        } else {
            expandedCountries.add(countryCode)
        }

        // Find the header position
        val headerPos = displayItems.indexOfFirst {
            it is DisplayItem.CountryHeader && it.countryCode == countryCode
        }
        if (headerPos < 0) return

        val servers = countryGroups[countryCode] ?: return
        val isNowExpanded = countryCode in expandedCountries

        // Update header
        val oldHeader = displayItems[headerPos] as DisplayItem.CountryHeader
        displayItems[headerPos] = oldHeader.copy(isExpanded = isNowExpanded)
        notifyItemChanged(headerPos)

        if (isNowExpanded) {
            // Insert server items after header
            val entries = servers.map { DisplayItem.ServerEntry(it) }
            displayItems.addAll(headerPos + 1, entries)
            notifyItemRangeInserted(headerPos + 1, entries.size)
        } else {
            // Remove server items after header
            val removeCount = servers.size
            for (i in 0 until removeCount) {
                displayItems.removeAt(headerPos + 1)
            }
            notifyItemRangeRemoved(headerPos + 1, removeCount)
        }
    }

    fun hasCountryGroups(): Boolean {
        return displayItems.any { it is DisplayItem.CountryHeader }
    }

    override fun getItemCount() = displayItems.size + 1

    override fun getItemViewType(position: Int): Int {
        if (position >= displayItems.size) return VIEW_TYPE_FOOTER
        return when (displayItems[position]) {
            is DisplayItem.CountryHeader -> VIEW_TYPE_COUNTRY
            is DisplayItem.ServerEntry -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_COUNTRY ->
                CountryViewHolder(ItemRecyclerCountryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            VIEW_TYPE_ITEM ->
                MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (position >= displayItems.size) return

        when (val item = displayItems[position]) {
            is DisplayItem.CountryHeader -> bindCountryHeader(holder as CountryViewHolder, item)
            is DisplayItem.ServerEntry -> bindServerItem(holder as MainViewHolder, item.serversCache, position)
        }
    }

    private fun bindCountryHeader(holder: CountryViewHolder, header: DisplayItem.CountryHeader) {
        val binding = holder.binding

        if (header.flag.isNotEmpty()) {
            binding.tvCountryFlag.text = header.flag
            binding.tvCountryFlag.visibility = View.VISIBLE
        } else {
            binding.tvCountryFlag.visibility = View.GONE
        }

        binding.tvCountryName.text = header.countryName

        val countText = if (header.serverCount == 1) "1 server" else "${header.serverCount} servers"
        binding.tvServerCount.text = countText

        binding.ivExpand.setImageResource(
            if (header.isExpanded) R.drawable.ic_expand_less_24dp
            else R.drawable.ic_expand_more_24dp
        )

        binding.root.setOnClickListener {
            toggleCountry(header.countryCode)
        }
    }

    private fun bindServerItem(holder: MainViewHolder, cache: ServersCache, position: Int) {
        val context = holder.itemMainBinding.root.context
        val guid = cache.guid
        val profile = cache.profile

        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        //Country flag
        val flag = CountryUtil.getFlagForProfile(profile.remarks, profile.server)
        if (flag.isNotEmpty()) {
            holder.itemMainBinding.tvCountryFlag.text = flag
            holder.itemMainBinding.tvCountryFlag.visibility = View.VISIBLE
        } else {
            holder.itemMainBinding.tvCountryFlag.visibility = View.GONE
        }

        //Name address
        holder.itemMainBinding.tvName.text = profile.remarks
        holder.itemMainBinding.tvStatistics.text = getAddress(profile)
        holder.itemMainBinding.tvType.text = profile.configType.name

        //TestResult
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
        if ((aff?.testDelayMillis ?: 0L) < 0L) {
            holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPingRed))
        } else {
            holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPing))
        }

        //layoutIndicator
        if (guid == MmkvManager.getSelectServer()) {
            holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.colorIndicator)
        } else {
            holder.itemMainBinding.layoutIndicator.setBackgroundResource(0)
        }

        //subscription remarks
        val subRemarks = getSubscriptionRemarks(profile)
        holder.itemMainBinding.tvSubscription.text = subRemarks
        holder.itemMainBinding.layoutSubscription.visibility = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

        //layout
        if (doubleColumnDisplay) {
            holder.itemMainBinding.layoutShare.visibility = View.GONE
            holder.itemMainBinding.layoutEdit.visibility = View.GONE
            holder.itemMainBinding.layoutRemove.visibility = View.GONE
            holder.itemMainBinding.layoutMore.visibility = View.VISIBLE

            holder.itemMainBinding.layoutMore.setOnClickListener {
                adapterListener?.onShare(guid, profile, position, true)
            }
        } else {
            holder.itemMainBinding.layoutShare.visibility = View.VISIBLE
            holder.itemMainBinding.layoutEdit.visibility = View.VISIBLE
            holder.itemMainBinding.layoutRemove.visibility = View.VISIBLE
            holder.itemMainBinding.layoutMore.visibility = View.GONE

            holder.itemMainBinding.layoutShare.setOnClickListener {
                adapterListener?.onShare(guid, profile, position, false)
            }

            holder.itemMainBinding.layoutEdit.setOnClickListener {
                adapterListener?.onEdit(guid, position, profile)
            }
            holder.itemMainBinding.layoutRemove.setOnClickListener {
                adapterListener?.onRemove(guid, position)
            }
        }

        holder.itemMainBinding.infoContainer.setOnClickListener {
            adapterListener?.onSelectServer(guid)
        }
    }

    private fun getAddress(profile: ProfileItem): String {
        return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
    }

    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        val subRemarks =
            if (mainViewModel.subscriptionId.isEmpty())
                MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            else
                null
        return subRemarks?.toString() ?: ""
    }

    fun removeServerSub(guid: String, position: Int) {
        // Remove from display list
        val displayPos = displayItems.indexOfFirst {
            it is DisplayItem.ServerEntry && it.serversCache.guid == guid
        }
        if (displayPos >= 0) {
            displayItems.removeAt(displayPos)
            notifyItemRemoved(displayPos)
            notifyItemRangeChanged(displayPos, displayItems.size - displayPos)
        }

        // Update country group
        for ((code, servers) in countryGroups) {
            val removed = servers.removeAll { it.guid == guid }
            if (removed) {
                // Update the header server count
                val headerPos = displayItems.indexOfFirst {
                    it is DisplayItem.CountryHeader && it.countryCode == code
                }
                if (headerPos >= 0) {
                    val header = displayItems[headerPos] as DisplayItem.CountryHeader
                    if (servers.isEmpty()) {
                        // Remove empty country header
                        displayItems.removeAt(headerPos)
                        notifyItemRemoved(headerPos)
                        countryGroups.remove(code)
                        expandedCountries.remove(code)
                    } else {
                        displayItems[headerPos] = header.copy(serverCount = servers.size)
                        notifyItemChanged(headerPos)
                    }
                }
                break
            }
        }

        // Remove from allServers
        allServers.removeAll { it.guid == guid }
    }

    fun setSelectServer(fromPosition: Int, toPosition: Int) {
        // Translate serversCache positions to display positions
        val fromGuid = mainViewModel.serversCache.getOrNull(fromPosition)?.guid
        val toGuid = mainViewModel.serversCache.getOrNull(toPosition)?.guid

        if (fromGuid != null) {
            val displayPos = displayItems.indexOfFirst {
                it is DisplayItem.ServerEntry && it.serversCache.guid == fromGuid
            }
            if (displayPos >= 0) notifyItemChanged(displayPos)
        }

        if (toGuid != null) {
            val displayPos = displayItems.indexOfFirst {
                it is DisplayItem.ServerEntry && it.serversCache.guid == toGuid
            }
            if (displayPos >= 0) notifyItemChanged(displayPos)
        }
    }

    /**
     * Finds the display position of a server by its GUID.
     * Returns -1 if not found in the current display list.
     */
    fun findDisplayPosition(guid: String): Int {
        return displayItems.indexOfFirst {
            it is DisplayItem.ServerEntry && it.serversCache.guid == guid
        }
    }

    /**
     * Ensures the country containing the given server GUID is expanded.
     * Returns the display position of the server, or -1 if not found.
     */
    fun expandAndFindServer(guid: String): Int {
        // Find which country this server belongs to
        for ((code, servers) in countryGroups) {
            if (servers.any { it.guid == guid }) {
                if (code !in expandedCountries) {
                    toggleCountry(code)
                }
                return findDisplayPosition(guid)
            }
        }
        return -1
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class CountryViewHolder(val binding: ItemRecyclerCountryBinding) :
        BaseViewHolder(binding.root)

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        // Only allow moving server items, not country headers
        if (fromPosition >= displayItems.size || toPosition >= displayItems.size) return false
        if (displayItems[fromPosition] !is DisplayItem.ServerEntry) return false
        if (displayItems[toPosition] !is DisplayItem.ServerEntry) return false

        mainViewModel.swapServer(fromPosition, toPosition)
        Collections.swap(displayItems, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        // do nothing
    }

    override fun onItemDismiss(position: Int) {
    }
}
