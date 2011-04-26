package com.gitblit.wicket.panels;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.models.PathModel.PathChangeModel;
import com.gitblit.wicket.pages.BlobDiffPage;
import com.gitblit.wicket.pages.BlobPage;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.HistoryPage;
import com.gitblit.wicket.pages.SearchPage;
import com.gitblit.wicket.pages.TreePage;

public class HistoryPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private boolean hasMore = false;

	public HistoryPanel(String wicketId, final String repositoryName, final String objectId, final String path, Repository r, int limit, int pageOffset) {
		super(wicketId);
		boolean pageResults = limit <= 0;
		int itemsPerPage = GitBlit.self().settings().getInteger(Keys.web.itemsPerPage, 50);
		if (itemsPerPage <= 1) {
			itemsPerPage = 50;
		}

		RevCommit commit = JGitUtils.getCommit(r, objectId);
		List<PathChangeModel> paths = JGitUtils.getFilesInCommit(r, commit);

		PathModel matchingPath = null;
		for (PathModel p : paths) {
			if (p.path.equals(path)) {
				matchingPath = p;
				break;
			}
		}
		final boolean isTree = matchingPath == null ? true : matchingPath.isTree();

		final Map<ObjectId, List<String>> allRefs = JGitUtils.getAllRefs(r);
		List<RevCommit> commits;
		if (pageResults) {
			// Paging result set
			commits = JGitUtils.getRevLog(r, objectId, path, pageOffset * itemsPerPage, itemsPerPage);
		} else {
			// Fixed size result set
			commits = JGitUtils.getRevLog(r, objectId, path, 0, limit);
		}

		// inaccurate way to determine if there are more commits.
		// works unless commits.size() represents the exact end.
		hasMore = commits.size() >= itemsPerPage;

		add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

		// breadcrumbs
		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, path, objectId));

		ListDataProvider<RevCommit> dp = new ListDataProvider<RevCommit>(commits);
		DataView<RevCommit> logView = new DataView<RevCommit>("commit", dp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RevCommit> item) {
				final RevCommit entry = item.getModelObject();
				final Date date = JGitUtils.getCommitDate(entry);

				item.add(WicketUtils.createDateLabel("commitDate", date, getTimeZone()));

				// author search link
				String author = entry.getAuthorIdent().getName();
				LinkPanel authorLink = new LinkPanel("commitAuthor", "list", author, SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, author, SearchType.AUTHOR));
				setPersonSearchTooltip(authorLink, author, SearchType.AUTHOR);
				item.add(authorLink);

				// merge icon
				if (entry.getParentCount() > 1) {
					item.add(WicketUtils.newImage("commitIcon", "commit_merge_16x16.png"));
				} else {
					item.add(WicketUtils.newBlankImage("commitIcon"));
				}

				String shortMessage = entry.getShortMessage();
				String trimmedMessage = StringUtils.trimShortLog(shortMessage);
				LinkPanel shortlog = new LinkPanel("commitShortMessage", "list subject", trimmedMessage, CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName()));
				if (!shortMessage.equals(trimmedMessage)) {
					WicketUtils.setHtmlTooltip(shortlog, shortMessage);
				}
				item.add(shortlog);

				item.add(new RefsPanel("commitRefs", repositoryName, entry, allRefs));

				if (isTree) {
					Fragment links = new Fragment("historyLinks", "treeLinks", this);
					links.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
					links.add(new BookmarkablePageLink<Void>("commitdiff", CommitDiffPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
					item.add(links);
				} else {
					Fragment links = new Fragment("historyLinks", "blobLinks", this);
					links.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils.newPathParameter(repositoryName, entry.getName(), path)));
					links.add(new BookmarkablePageLink<Void>("commitdiff", CommitDiffPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
					links.add(new BookmarkablePageLink<Void>("difftocurrent", BlobDiffPage.class, WicketUtils.newBlobDiffParameter(repositoryName, entry.getName(), objectId, path)).setEnabled(counter > 0));
					item.add(links);
				}

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(logView);

		// determine to show pager, more, or neither
		if (limit <= 0) {
			// no display limit
			add(new Label("moreHistory", "").setVisible(false));
		} else {
			if (pageResults) {
				// paging
				add(new Label("moreHistory", "").setVisible(false));
			} else {
				// more
				if (commits.size() == limit) {
					// show more
					add(new LinkPanel("moreHistory", "link", new StringResourceModel("gb.moreHistory", this, null), HistoryPage.class, WicketUtils.newPathParameter(repositoryName, objectId, path)));
				} else {
					// no more
					add(new Label("moreHistory", "").setVisible(false));
				}
			}
		}
	}

	public boolean hasMore() {
		return hasMore;
	}
}
