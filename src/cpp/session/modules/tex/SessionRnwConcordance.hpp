/*
 * SessionRnwConcordance.hpp
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#ifndef SESSION_MODULES_RNW_CONCORDANCE_HPP
#define SESSION_MODULES_RNW_CONCORDANCE_HPP

#include <string>
#include <vector>

#include <boost/utility.hpp>

#include <core/FilePath.hpp>
#include <core/SafeConvert.hpp>
#include <core/tex/TexLogParser.hpp>

namespace core {
   class Error;
}
 
namespace rsession {
namespace modules { 
namespace tex {
namespace rnw_concordance {

class Concordance
{
public:
   Concordance()
      : offset_(0)
   {
   }

   // COPYING: via compiler

   // create by parsing a concordance file
   core::Error parse(const core::FilePath& sourceFile,
                     const std::string& input,
                     const core::FilePath& baseDir);

   // append another concordance to this concordance (assumes they have
   // the same input and output file and they originate from a common
   // concordance generation sequences -- i.e. offsets line up)
   void append(const Concordance& concordance);

   bool empty() const { return mapping_.empty(); }

   const core::FilePath& outputFile() const { return outputFile_; }

   const core::FilePath& inputFile() const { return inputFile_; }

   std::size_t offset() const { return offset_; }

   // checked access to rnw lines from tex lines
   int rnwLine(int texLine) const
   {
      // subtract 1 to normalize lines to C array indexes
      texLine--;

      // then substract the offset (which was the starting line
      // number of the output generated by the input file)
      texLine -= offset_;

      // return the mapping (but return -1 if it is out of range)
      int mappingSize = core::safe_convert::numberTo<int>(mapping_.size(), 0);
      if (texLine >= 0 && texLine < mappingSize)
         return mapping_[texLine];
      else
         return -1;
   }

   // checked access to tex lines from rnw lines. note that this returns
   // the tex line which is closest to the specified rnw line (since some
   // rnw lines don't result in tex output e.g. ones in hidden sweave chunks)
   int texLine(int rnwLine) const
   {
      int texLine = -1;
      int smallestDistance = -1;
      for (std::size_t i = 0; i<mapping_.size(); i++)
      {
         if (texLine == -1)
         {
            texLine = i + 1 + offset_;
            smallestDistance = std::abs(mapping_[i] - rnwLine);
         }
         else
         {
            int thisDistance = std::abs(mapping_[i] - rnwLine);
            if (thisDistance < smallestDistance)
            {
               texLine = i + 1 + offset_;
               smallestDistance = thisDistance;

               if (smallestDistance == 0)
                  break;
            }
         }
      }

      return texLine;
   }

private:
   core::FilePath outputFile_;
   core::FilePath inputFile_;
   std::size_t offset_;
   std::vector<int> mapping_;
};

class FileAndLine
{
public:
   FileAndLine()
      : line_(-1)
   {
   }

   FileAndLine(const core::FilePath& filePath, int line)
      : filePath_(filePath), line_(line)
   {
   }
   ~FileAndLine() {}
   // COPYING: via compiler

   bool empty() const { return filePath_.empty(); }

   const core::FilePath& filePath() const { return filePath_; }
   int line() const { return line_; }

private:
   core::FilePath filePath_;
   int line_;
};

std::ostream& operator << (std::ostream& stream, const FileAndLine& fileLine);

class Concordances
{
public:
   Concordances() {}
   ~Concordances() {}
   // COPYING: via compiler

   bool empty() const { return concordances_.empty(); }

   void add(Concordance& concordance)
   {
      concordances_.push_back(concordance);
   }

   FileAndLine rnwLine(const FileAndLine& texLine) const;
   FileAndLine texLine(const FileAndLine& rnwLine) const;

   core::tex::LogEntry fixup(const core::tex::LogEntry& entry,
                             bool* pSuccess=NULL) const;

private:
   std::vector<Concordance> concordances_;
};

void removePrevious(const core::FilePath& rnwFile);

core::Error readIfExists(const core::FilePath& srcFile,
                         Concordances* pConcordances);

} // namespace rnw_concordance
} // namespace tex
} // namespace modules
} // namesapce session

#endif // SESSION_MODULES_RNW_CONCORDANCE_HPP
