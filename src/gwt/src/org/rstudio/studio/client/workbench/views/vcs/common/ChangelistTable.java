/*
 * ChangelistTable.java
 *
 * Copyright (C) 2009-11 by RStudio, Inc.
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.vcs.common;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.text.shared.SafeHtmlRenderer;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.view.client.*;
import org.rstudio.core.client.theme.RStudioCellTableStyle;
import org.rstudio.core.client.widget.MultiSelectCellTable;
import org.rstudio.core.client.widget.ProgressPanel;
import org.rstudio.studio.client.common.vcs.StatusAndPath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public abstract class ChangelistTable extends Composite
   implements HasKeyDownHandlers, HasClickHandlers, HasMouseDownHandlers
{
   public interface ChangelistTableCellTableResources extends CellTable.Resources
   {
      @Override
      @Source("ascendingArrow.png")
      ImageResource cellTableSortAscending();

      @Override
      @Source("descendingArrow.png")
      ImageResource cellTableSortDescending();

      @Override
      @Source({RStudioCellTableStyle.RSTUDIO_DEFAULT_CSS,
               "ChangelistTableCellTableStyle.css"})
      ChangelistTableCellTableStyle cellTableStyle();
   }

   public interface ChangelistTableCellTableStyle extends CellTable.Style
   {
      String status();
   }

   /**
    * The whole point of this subclass is to force CellTable to update its
    * cellIsEditing private member more often. This is to work around a bug
    * where the ChangelistTable can get into a state where mouse clicks don't
    * change the selection, because TriStateCheckboxCell gets a mouseover event
    * but not a matching mouseout.
    */
   protected static class NotEditingTextCell extends TextCell
   {
      public NotEditingTextCell()
      {
         super();
         init();
      }

      public NotEditingTextCell(SafeHtmlRenderer<String> renderer)
      {
         super(renderer);
         init();
      }

      private void init()
      {
         Set<String> inheritedEvents = getConsumedEvents();
         if (inheritedEvents != null)
            consumedEvents_.addAll(inheritedEvents);
         consumedEvents_.add("mouseover");
      }

      @Override
      public Set<String> getConsumedEvents()
      {
         return consumedEvents_;
      }

      @Override
      public boolean isEditing(Context context, Element parent, String value)
      {
         return false;
      }

      private Set<String> consumedEvents_ = new HashSet<String>();
   }

   public ChangelistTable()
   {
      table_ = new MultiSelectCellTable<StatusAndPath>(100, resources_);

      dataProvider_ = new ListDataProvider<StatusAndPath>();
      sortHandler_ = new ColumnSortEvent.ListHandler<StatusAndPath>(
            dataProvider_.getList());
      table_.addColumnSortHandler(sortHandler_);

      selectionModel_ = new MultiSelectionModel<StatusAndPath>(
            new ProvidesKey<StatusAndPath>()
            {
               @Override
               public Object getKey(StatusAndPath item)
               {
                  return item.getPath();
               }
            });
      table_.setSelectionModel(selectionModel_);
      dataProvider_.addDataDisplay(table_);

      configureTable();

      table_.setSize("100%", "auto");

      layout_ = new LayoutPanel();
      ScrollPanel scrollPanel = new ScrollPanel(table_);
      layout_.add(scrollPanel);
      layout_.setWidgetTopBottom(scrollPanel, 0, Unit.PX, 0, Unit.PX);
      layout_.setWidgetLeftRight(scrollPanel, 0, Unit.PX, 0, Unit.PX);
      progressPanel_ = new ProgressPanel();
      progressPanel_.getElement().getStyle().setBackgroundColor("white");
      layout_.add(progressPanel_);
      layout_.setWidgetTopBottom(progressPanel_, 0, Unit.PX, 0, Unit.PX);
      layout_.setWidgetLeftRight(progressPanel_, 0, Unit.PX, 0, Unit.PX);

      setProgress(true);

      initWidget(layout_);
   }

   protected abstract SafeHtmlRenderer<String> getStatusRenderer();

   public void setSelectFirstItemByDefault(boolean selectFirstItemByDefault)
   {
      selectFirstItemByDefault_ = selectFirstItemByDefault;
   }

   public void moveSelectionDown()
   {
      if (getSelectedItems().size() == 1)
         {
            table_.moveSelection(false, false);
         }
   }

   public void showProgress()
   {
      setProgress(true);
   }

   protected void setProgress(boolean showProgress)
   {
      if (showProgress)
      {
         layout_.setWidgetVisible(progressPanel_, true);
         progressPanel_.beginProgressOperation(300);
      }
      else
      {
         layout_.setWidgetVisible(progressPanel_, false);
         progressPanel_.endProgressOperation();
      }
   }

   protected void configureTable()
   {
      Column<StatusAndPath, String> statusColumn = new Column<StatusAndPath, String>(
            new NotEditingTextCell(getStatusRenderer()))
      {
         @Override
         public String getValue(StatusAndPath object)
         {
            return object.getStatus();
         }
      };
      statusColumn.setSortable(true);
      statusColumn.setHorizontalAlignment(Column.ALIGN_CENTER);
      table_.addColumn(statusColumn, "Status");
      table_.setColumnWidth(statusColumn, "56px");
      sortHandler_.setComparator(statusColumn, new Comparator<StatusAndPath>()
      {
         @Override
         public int compare(StatusAndPath a, StatusAndPath b)
         {
            return a.getStatus().compareTo(b.getStatus());
         }
      });

      Column<StatusAndPath, String> pathColumn = new Column<StatusAndPath, String>(
            new NotEditingTextCell())
      {
         @Override
         public String getValue(StatusAndPath object)
         {
            return object.getPath();
         }
      };
      pathColumn.setSortable(true);
      sortHandler_.setComparator(pathColumn, new Comparator<StatusAndPath>()
      {
         private String[] splitDirAndName(String path)
         {
            int index = path.lastIndexOf("/");
            if (index < 0)
               index = path.lastIndexOf("\\");
            if (index < 0)
               return new String[] { "", path };
            else
               return new String[] { path.substring(0, index),
                                     path.substring(index + 1) };
         }

         @Override
         public int compare(StatusAndPath a, StatusAndPath b)
         {
            String[] splitA = splitDirAndName(a.getPath());
            String[] splitB = splitDirAndName(b.getPath());
            int result = splitA[0].compareTo(splitB[0]);
            if (result == 0)
               result = splitA[1].compareTo(splitB[1]);
            return result;
         }
      });
      table_.addColumn(pathColumn, "Path");

      table_.getColumnSortList().push(pathColumn);
   }

   public HandlerRegistration addSelectionChangeHandler(
         SelectionChangeEvent.Handler handler)
   {
      return selectionModel_.addSelectionChangeHandler(handler);
   }

   public void setItems(ArrayList<StatusAndPath> items)
   {
      setProgress(false);
      table_.setPageSize(items.size());
      dataProvider_.getList().clear();
      dataProvider_.getList().addAll(items);
      ColumnSortEvent.fire(table_,
                           table_.getColumnSortList());

      if (selectFirstItemByDefault_)
      {
         Scheduler.get().scheduleDeferred(new ScheduledCommand()
         {
            @Override
            public void execute()
            {
               if (table_.getVisibleItemCount() > 0
                   && selectionModel_.getSelectedSet().isEmpty())
               {
                  selectionModel_.setSelected(table_.getVisibleItem(0), true);
               }
            }
         });
      }
   }

   public ArrayList<StatusAndPath> getSelectedItems()
   {
      SelectionModel<? super StatusAndPath> selectionModel = table_.getSelectionModel();

      ArrayList<StatusAndPath> results = new ArrayList<StatusAndPath>();
      for (StatusAndPath item : dataProvider_.getList())
      {
         if (selectionModel.isSelected(item))
            results.add(item);
      }
      return results;
   }

   public ArrayList<String> getSelectedPaths()
   {
      SelectionModel<? super StatusAndPath> selectionModel = table_.getSelectionModel();

      ArrayList<String> results = new ArrayList<String>();
      for (StatusAndPath item : dataProvider_.getList())
      {
         if (selectionModel.isSelected(item))
            results.add(item.getPath());
      }
      return results;
   }

   public void setSelectedStatusAndPaths(ArrayList<StatusAndPath> selectedPaths)
   {
      selectionModel_.clear();
      for (StatusAndPath path : selectedPaths)
         selectionModel_.setSelected(path, true);
   }

   public ArrayList<String> getSelectedDiscardablePaths()
   {
      SelectionModel<? super StatusAndPath> selectionModel = table_.getSelectionModel();

      ArrayList<String> results = new ArrayList<String>();
      for (StatusAndPath item : dataProvider_.getList())
      {
         if (selectionModel.isSelected(item) && item.isDiscardable())
            results.add(item.getPath());
      }
      return results;
   }

   public void selectNextUnselectedItem()
   {
      boolean selectNext = false;
      for (StatusAndPath path : table_.getVisibleItems())
      {
         if (selectionModel_.isSelected(path))
            selectNext = true;
         else if (selectNext)
         {
            ArrayList<StatusAndPath> selection = new ArrayList<StatusAndPath>();
            selection.add(path);
            setSelectedStatusAndPaths(selection);
            return;
         }
      }
   }

   @Override
   public HandlerRegistration addKeyDownHandler(KeyDownHandler handler)
   {
      return table_.addKeyDownHandler(handler);
   }

   @Override
   public HandlerRegistration addClickHandler(ClickHandler handler)
   {
      return table_.addClickHandler(handler);
   }

   @Override
   public HandlerRegistration addMouseDownHandler(MouseDownHandler handler)
   {
      return table_.addMouseDownHandler(handler);
   }

   public void focus()
   {
      table_.setFocus(true);
   }

   protected final MultiSelectCellTable<StatusAndPath> table_;
   protected final MultiSelectionModel<StatusAndPath> selectionModel_;
   protected final ColumnSortEvent.ListHandler<StatusAndPath> sortHandler_;
   private final ListDataProvider<StatusAndPath> dataProvider_;
   private final ProgressPanel progressPanel_;
   private LayoutPanel layout_;
   private boolean selectFirstItemByDefault_;
   private static final ChangelistTableCellTableResources resources_ = GWT.<ChangelistTableCellTableResources>create(ChangelistTableCellTableResources.class);
}
