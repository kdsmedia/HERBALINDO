package com.altomedia.herbalindo.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.altomedia.herbalindo.data.Repository
import com.altomedia.herbalindo.data.model.AdminLog
import com.altomedia.herbalindo.databinding.FragmentAdminLogsBinding
import com.altomedia.herbalindo.databinding.ItemLedgerBinding
import com.altomedia.herbalindo.util.Fmt
import kotlinx.coroutines.launch

/** Immutable audit trail of every privileged action (BAB 10.6). */
class AdminLogsFragment : Fragment() {

    private var _binding: FragmentAdminLogsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = LogAdapter()
        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            Repository.adminLogsFlow().collect { logs ->
                _binding ?: return@collect
                adapter.submit(logs)
                val empty = logs.isEmpty()
                binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvLogs.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvLogs.adapter = null
        _binding = null
    }

    private class LogAdapter : ListAdapter<AdminLog, LogAdapter.VH>(DIFF) {

        fun submit(items: List<AdminLog>) = submitList(items)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemLedgerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        class VH(private val b: ItemLedgerBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(log: AdminLog) {
                b.tvTitle.text = log.action
                b.tvSubtitle.text = buildString {
                    append(Fmt.dateTime(log.timestamp))
                    if (log.target.isNotBlank()) append(" · ${log.target}")
                    if (log.detail.isNotBlank()) append("\n${log.detail}")
                }
                b.tvAmount.text = if (log.amount != 0L) Fmt.thousands(log.amount) else ""
                b.ivIcon.setImageResource(
                    com.altomedia.herbalindo.R.drawable.ic_history
                )
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<AdminLog>() {
                override fun areItemsTheSame(a: AdminLog, b: AdminLog) = a.logId == b.logId
                override fun areContentsTheSame(a: AdminLog, b: AdminLog) = a == b
            }
        }
    }
}